package com.bteamore.configswitch.config;

import com.bteamore.configswitch.core.PackReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RepoPacker 的打包与解包：白名单条目、manifest 校验、zip-slip 防护、失败时的落盘行为。
 * 用例都在临时目录里造仓库和包，不碰真实仓库。
 */
public class RepoPackerTest {
    @TempDir
    Path tempDir;

    // 格式标记正确的 manifest，字段形态与 pack 写出的一致
    private static final String VALID_MANIFEST = manifestJson(PackManifest.DEFAULT_FORMAT);

    // ---------- 造仓库与造包 ----------

    private Path repoRoot(String name) throws IOException {
        return Files.createDirectories(tempDir.resolve(name));
    }

    // 每个用例现造一个解包目标，互不干扰
    private Path newTarget() throws IOException {
        return Files.createTempDirectory(tempDir, "target-");
    }

    private static Path write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static String read(Path root, String relative) throws IOException {
        return Files.readString(root.resolve(relative));
    }

    private static List<Path> children(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.toList();
        }
    }

    // 手搓 zip：条目按传入顺序成对给出（名称, 内容），方便造畸形包
    private Path zipOf(String... nameAndContent) throws IOException {
        Path zip = Files.createTempFile(tempDir, "fixture-", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (int i = 0; i < nameAndContent.length; i += 2) {
                zos.putNextEntry(new ZipEntry(nameAndContent[i]));
                zos.write(nameAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zip;
    }

    private static String manifestJson(int format) {
        return "{\"format\":" + format + ",\"exportedAt\":\"2026-01-01T00:00:00Z\",\"modVersion\":\"0.0.0\"}";
    }

    private static PackManifest newManifest() {
        return new PackManifest(PackManifest.DEFAULT_FORMAT, "2026-01-01T00:00:00Z", "0.0.0");
    }

    private static String entryContent(ZipFile zipFile, String name) throws IOException {
        ZipEntry entry = zipFile.getEntry(name);
        assertNotNull(entry, name);
        try (InputStream in = zipFile.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // pack/unpack 都不该抛异常，统一包一层，顺便规避 assertDoesNotThrow 的重载歧义
    private static boolean pack(Path root, Path output, PackManifest manifest) {
        return packReport(root, output, manifest).outcome() == PackReport.outcome.SUCCESS;
    }

    private static boolean unpack(Path root, Path input) {
        return unpackReport(root, input).outcome() == PackReport.outcome.SUCCESS;
    }

    private static PackReport packReport(Path root, Path output, PackManifest manifest) {
        return assertDoesNotThrow((ThrowingSupplier<PackReport>) () -> RepoPacker.pack(root, output, manifest));
    }

    private static PackReport unpackReport(Path root, Path input) {
        return assertDoesNotThrow((ThrowingSupplier<PackReport>) () -> RepoPacker.unpack(root, input));
    }

    // ---------- 打包 ----------

    // 白名单里的进包：global 目录（含子目录与目录条目）、mappings.txt；白名单外的文件不打包
    @Test
    void testPackWritesManifestAndWhiteListedFiles() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "global/a.json", "A");
        write(repo, "global/sub/b.json", "B");
        write(repo, "mappings.txt", "M");
        write(repo, "other/c.json", "C");

        Path zip = tempDir.resolve("out.zip");
        assertTrue(pack(repo, zip, newManifest()));

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            assertNotNull(zipFile.getEntry(PackManifest.ENTRY_NAME), "缺少 manifest 条目");
            assertNotNull(zipFile.getEntry("global/"), "缺少 global 目录条目");
            assertEquals("A", entryContent(zipFile, "global/a.json"));
            assertEquals("B", entryContent(zipFile, "global/sub/b.json"));
            assertEquals("M", entryContent(zipFile, "mappings.txt"));
            assertNull(zipFile.getEntry("other/c.json"), "白名单外的文件不该进包");
            assertNull(zipFile.getEntry("other/"));
        }
    }

    // 导出→导入闭环：文件内容原样回来，manifest 是元数据，不解到仓库里
    @Test
    void testPackThenUnpackRestoresFiles() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "global/a.json", "A");
        write(repo, "global/sub/b.json", "B");
        write(repo, "mappings.txt", "M");

        Path zip = tempDir.resolve("out.zip");
        assertTrue(pack(repo, zip, newManifest()));

        Path target = newTarget();
        assertTrue(unpack(target, zip));

        assertEquals("A", read(target, "global/a.json"));
        assertEquals("B", read(target, "global/sub/b.json"));
        assertEquals("M", read(target, "mappings.txt"));
        assertFalse(Files.exists(target.resolve(PackManifest.ENTRY_NAME)), "manifest 不该解到仓库里");
    }

    // 导出目标打不开（父级被普通文件占位）→ 返回 false，不抛异常，占位文件不动
    @Test
    void testPackFailsWhenOutputCannotBeOpened() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "mappings.txt", "M");
        Path blocked = write(tempDir, "blocked", "not a directory");

        assertFalse(pack(repo, blocked.resolve("out.zip"), newManifest()));

        assertEquals("not a directory", Files.readString(blocked));
    }

    // 仓库里没有 mappings.txt → 打包失败
    @Test
    void testPackFailsWithoutMappings() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "global/a.json", "A");

        assertFalse(pack(repo, tempDir.resolve("out.zip"), newManifest()));
    }

    // 打包失败留下的残包不能被当成合法包：否则导入会报成功，却什么都没恢复
    @Test
    void testFailedPackLeavesNoUsablePackage() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "global/a.json", "A");

        Path zip = tempDir.resolve("out.zip");
        assertFalse(pack(repo, zip, newManifest()));

        assertFalse(unpack(newTarget(), zip), "残包不该被接受");
    }

    // ---------- 解包拒绝：打不开的包 ----------

    // 根本不是 zip、被截断的包、不存在的文件都拒绝，目标目录保持为空
    @Test
    void testUnpackRejectsFileThatIsNotAZip() throws IOException {
        Path notZip = write(tempDir, "not-a-zip.zip", "this is plain text");
        assertFalse(unpack(newTarget(), notZip));

        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/a.json", "A");
        byte[] bytes = Files.readAllBytes(zip);
        Path truncated = tempDir.resolve("truncated.zip");
        Files.write(truncated, Arrays.copyOf(bytes, bytes.length / 2));

        Path target = newTarget();
        assertFalse(unpack(target, truncated));
        assertTrue(children(target).isEmpty());

        assertFalse(unpack(newTarget(), tempDir.resolve("no-such-package.zip")));
    }

    // ---------- 解包拒绝：格式标记 ----------

    // 没有 manifest 条目 → 拒绝，且一个文件都不解出来
    @Test
    void testUnpackRejectsZipWithoutManifest() throws IOException {
        Path target = newTarget();
        Path zip = zipOf("global/a.json", "A", "mappings.txt", "M");

        assertFalse(unpack(target, zip));

        assertTrue(children(target).isEmpty());
    }

    // format 空 / JSON null / 缺 format 字段 / 认不出的 format 都拒绝；format 正确才放行
    @Test
    void testUnpackRejectsUnknownOrMissingFormat() throws IOException {
        List<String> rejected = List.of(
                "",
                "null",
                "{}",
                "{\"exportedAt\":\"2026-01-01T00:00:00Z\"}",
                manifestJson(PackManifest.DEFAULT_FORMAT + 1)
        );
        for (String manifest : rejected) {
            Path target = newTarget();
            Path zip = zipOf(PackManifest.ENTRY_NAME, manifest, "global/a.json", "A");

            assertFalse(unpack(target, zip), "该被拒绝的 manifest: " + manifest);
            assertTrue(children(target).isEmpty(), "该被拒绝的 manifest: " + manifest);
        }

        // 校验只看格式标记，其余字段缺失不拦
        Path target = newTarget();
        Path zip = zipOf(PackManifest.ENTRY_NAME, "{\"format\":" + PackManifest.DEFAULT_FORMAT + "}", "global/a.json", "A");

        assertTrue(unpack(target, zip));
        assertEquals("A", read(target, "global/a.json"));
    }

    // manifest 不是合法 JSON → 拒绝，而不是把解析异常抛给调用方
    @Test
    void testUnpackRejectsMalformedManifestJson() throws IOException {
        Path target = newTarget();
        Path zip = zipOf(PackManifest.ENTRY_NAME, "not json at all", "global/a.json", "A");

        assertFalse(unpack(target, zip));

        assertTrue(children(target).isEmpty());
    }

    // ---------- 解包拒绝：zip-slip ----------

    // 条目路径带 ../ → 拒绝，目标根之外不许出现任何文件
    @Test
    void testUnpackRejectsParentTraversal() throws IOException {
        Path target = newTarget();
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST,
                "../escape.txt", "pwned",
                "mappings.txt", "M");

        assertFalse(unpack(target, zip));

        assertFalse(Files.exists(tempDir.resolve("escape.txt")), "越界文件被写出来了");
        assertTrue(children(target).isEmpty());
    }

    // zip-slip 变体：global/../../escape.txt 同样越界，越界路径的目录也不该被建出来
    @Test
    void testUnpackRejectsNestedTraversal() throws IOException {
        Path target = newTarget();
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/../../escape.txt", "pwned");

        assertFalse(unpack(target, zip));

        assertFalse(Files.exists(tempDir.resolve("escape.txt")), "越界文件被写出来了");
        assertTrue(children(target).isEmpty());
    }

    // zip-slip 变体：Windows 下 \ 也是分隔符，..\escape.txt 一样越界
    @Test
    void testUnpackRejectsBackslashTraversal() throws IOException {
        assumeTrue(File.separatorChar == '\\', "仅 Windows：\\ 才当路径分隔符");

        Path target = newTarget();
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "..\\escape.txt", "pwned");

        assertFalse(unpack(target, zip));

        assertFalse(Files.exists(tempDir.resolve("escape.txt")), "越界文件被写出来了");
    }

    // ---------- 解包拒绝：路径冲突 ----------

    // 目录条目撞上已有的同名文件（target/global 是文件，条目是 global/）→ 拒绝，且不动已有文件
    @Test
    void testUnpackRejectsDirectoryEntryWhereFileExists() throws IOException {
        Path target = repoRoot("target");
        write(target, "global", "FILE");
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/", "");

        assertFalse(unpack(target, zip));

        assertEquals("FILE", read(target, "global"));
        assertEquals(List.of(target.resolve("global")), children(target), "拒绝时不该多出任何文件");
    }

    // 条目父级被普通文件挡路（target/global 是文件，条目是 global/a.json）→ 拒绝，且不动已有文件
    @Test
    void testUnpackRejectsEntryBlockedByFileParent() throws IOException {
        Path target = repoRoot("target");
        write(target, "global", "FILE");
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/a.json", "A");

        assertFalse(unpack(target, zip));

        assertEquals("FILE", read(target, "global"));
        assertEquals(List.of(target.resolve("global")), children(target), "拒绝时不该多出任何文件");
    }

    // 目标文件只读 → 拒绝，内容保持原样
    @Test
    void testUnpackRejectsReadOnlyTarget() throws IOException {
        Path target = repoRoot("target");
        Path existing = write(target, "mappings.txt", "ORIGINAL");
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "mappings.txt", "NEW");

        assumeTrue(existing.toFile().setWritable(false), "环境不支持把文件置为只读");
        try {
            assertFalse(unpack(target, zip));

            assertEquals("ORIGINAL", read(target, "mappings.txt"));
        } finally {
            // 只读文件在 Windows 上清不掉，TempDir 回收会失败
            assertTrue(existing.toFile().setWritable(true));
        }
    }

    // ---------- 解包：覆盖已有文件 ----------

    // 目标已有同名文件 → 正常覆盖，新增的校验不能把合法导入挡在门外
    @Test
    void testUnpackOverwritesExistingFiles() throws IOException {
        Path target = repoRoot("target");
        write(target, "global/keep.json", "OLD");
        write(target, "mappings.txt", "OLD");
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST,
                "global/keep.json", "NEW", "mappings.txt", "NEW");

        assertTrue(unpack(target, zip));

        assertEquals("NEW", read(target, "global/keep.json"));
        assertEquals("NEW", read(target, "mappings.txt"));
    }

    // ---------- 解包：本批条目自身冲突 ----------

    // 文件条目 global 排在嵌套条目 global/a.json 之前 → 整单拒绝，目标里不留任何东西
    @Test
    void testUnpackRejectsFileEntryBeforeNestedEntry() throws IOException {
        Path target = newTarget();
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global", "FILE", "global/a.json", "A");

        assertFalse(unpack(target, zip));

        assertTrue(children(target).isEmpty(), "拒绝时不该写出任何文件");
    }

    // 反过来排（嵌套条目在前、同名文件在后）也要拒绝：否则前一个条目已经落了盘
    @Test
    void testUnpackRejectsNestedEntryBeforeFileEntry() throws IOException {
        Path target = newTarget();
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/a.json", "A", "global", "FILE");

        assertFalse(unpack(target, zip));

        assertTrue(children(target).isEmpty(), "拒绝时不该写出任何文件");
    }

    // 同一个名字既是文件条目又是目录条目（两种排法）都拒绝
    @Test
    void testUnpackRejectsFileAndDirectoryEntriesForSameName() throws IOException {
        Path fileFirst = newTarget();
        assertFalse(unpack(fileFirst, zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global", "FILE", "global/", "")));
        assertTrue(children(fileFirst).isEmpty(), "拒绝时不该写出任何文件");

        Path directoryFirst = newTarget();
        assertFalse(unpack(directoryFirst, zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/", "", "global", "FILE")));
        assertTrue(children(directoryFirst).isEmpty(), "拒绝时不该写出任何文件");
    }

    // 目录条目排在它下面的文件条目之后不算冲突：两条目都能正常落盘
    @Test
    void testUnpackAllowsDirectoryEntryAfterItsFiles() throws IOException {
        Path target = newTarget();
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/a.json", "A", "global/", "");

        assertTrue(unpack(target, zip));

        assertEquals("A", read(target, "global/a.json"));
    }

    // ---------- 解包失败：保持原状 ----------

    // 条目 global 与目标里已有的 global 目录冲突 → 整单拒绝，已有文件原样保留
    @Test
    void testUnpackConflictKeepsExistingFiles() throws IOException {
        Path target = repoRoot("target");
        write(target, "global/keep.json", "KEEP");
        write(target, "mappings.txt", "ORIGINAL");
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global", "file-on-dir", "mappings.txt", "NEW");

        assertFalse(unpack(target, zip));

        assertEquals("KEEP", read(target, "global/keep.json"));
        assertEquals("ORIGINAL", read(target, "mappings.txt"));
    }

    // 冲突条目排在合法条目之后也整单拒绝：合法条目一个都不能落盘，目标保持原状
    @Test
    void testUnpackFailureDoesNotLeavePartialChanges() throws IOException {
        Path target = repoRoot("target");
        write(target, "mappings.txt", "ORIGINAL");
        write(target, "global/keep.json", "KEEP");
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "mappings.txt", "NEW", "global", "file-on-dir");

        assertFalse(unpack(target, zip));

        assertEquals("ORIGINAL", read(target, "mappings.txt"), "失败后目标必须保持原状");
        assertEquals("KEEP", read(target, "global/keep.json"));
    }

    // ---------- 结果分类 ----------

    // 拒绝要分出「不是包」「包坏了」「环境不让落」，调用方才能给出不同提示
    @Test
    void testUnpackOutcomeClassifiesRejections() throws IOException {
        Path target = newTarget();

        assertEquals(PackReport.outcome.NOT_A_PACKAGE,
                unpackReport(target, write(tempDir, "plain.zip", "plain text")).outcome());
        assertEquals(PackReport.outcome.NOT_A_PACKAGE,
                unpackReport(target, zipOf("global/a.json", "A")).outcome());
        assertEquals(PackReport.outcome.NOT_A_PACKAGE,
                unpackReport(target, zipOf(PackManifest.ENTRY_NAME, manifestJson(PackManifest.DEFAULT_FORMAT + 1))).outcome());
        assertEquals(PackReport.outcome.CORRUPTED,
                unpackReport(target, zipOf(PackManifest.ENTRY_NAME, "not json")).outcome());
        assertEquals(PackReport.outcome.CORRUPTED,
                unpackReport(target, zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "../escape.txt", "X")).outcome());
        assertEquals(PackReport.outcome.CORRUPTED,
                unpackReport(target, zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global", "FILE", "global/a.json", "A")).outcome());

        // 目标侧冲突不是包的错：换个落点就能成，归到环境问题
        write(target, "global", "FILE");
        assertEquals(PackReport.outcome.IO_FAILED,
                unpackReport(target, zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/a.json", "A")).outcome());
    }

    // 成功时报告要带上实际写出的文件数与包的位置
    @Test
    void testUnpackReportCountsRestoredFiles() throws IOException {
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST,
                "global/", "", "global/a.json", "A", "mappings.txt", "M");

        PackReport report = unpackReport(newTarget(), zip);

        assertEquals(PackReport.outcome.SUCCESS, report.outcome());
        assertEquals(2, report.fileCount());
        assertEquals(zip, report.location());
    }
}