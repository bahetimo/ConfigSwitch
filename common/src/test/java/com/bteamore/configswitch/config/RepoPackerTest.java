package com.bteamore.configswitch.config;

import com.bteamore.configswitch.core.PackReport;
import com.bteamore.configswitch.repo.RepoMeta;
import com.bteamore.configswitch.repo.RepoMetaStore;
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
import java.util.Optional;
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

    // 两源重载：仓库根与 mappingsFile 各给一个
    private static PackReport packTwo(Path repoRoot, Path mappingsFile, Path output) {
        return assertDoesNotThrow((ThrowingSupplier<PackReport>)
                () -> RepoPacker.pack(repoRoot, mappingsFile, output, newManifest()));
    }

    private static PackReport unpackTwo(Path repoRoot, Path mappingsFile, Path input) {
        return assertDoesNotThrow((ThrowingSupplier<PackReport>)
                () -> RepoPacker.unpack(repoRoot, mappingsFile, input));
    }

    // 包内条目名（去掉 manifest，含目录条目）：比两次打包是否产出同一份内容
    private static List<String> entryNames(Path zip) throws IOException {
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            return zipFile.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> !name.equals(PackManifest.ENTRY_NAME))
                    .sorted()
                    .toList();
        }
    }

    // 目录下所有文件的相对路径（分隔符统一成 /）：比两次解包的落盘结果
    private static List<String> relativeFiles(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace("\\", "/"))
                    .sorted()
                    .toList();
        }
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

    // ---------- 打包：来源缺失 ----------
    //
    // 两条来源各自独立：缺一条只跳过那一条（真实的仓库可能还没生成 mappings.txt，也可能还没 push 过内容）。
    // 但一条文件都没进包 = 没有可导出的东西，必须判失败：那种包只有 manifest，用户看到的是"导出成功"，
    // 导入回来还会顺手把 meta 删掉

    // 只有 global：mappings.txt 那条跳过，global 照常进包
    @Test
    void testPackSkipsMissingMappings() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "global/a.json", "A");

        Path zip = tempDir.resolve("out.zip");
        assertTrue(pack(repo, zip, newManifest()));

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            assertNotNull(zipFile.getEntry("global/"), "缺少 global 目录条目");
            assertEquals("A", entryContent(zipFile, "global/a.json"));
            assertNull(zipFile.getEntry("mappings.txt"), "没有来源就不该凭空造一条 mappings.txt");
        }
    }

    // 只有 mappings.txt：global 那条跳过，mappings 照常进包
    @Test
    void testPackSkipsMissingGlobal() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "mappings.txt", "M");
        write(repo, "other/c.json", "C");

        Path zip = tempDir.resolve("out.zip");
        assertTrue(pack(repo, zip, newManifest()));

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            assertEquals("M", entryContent(zipFile, "mappings.txt"));
            assertNull(zipFile.getEntry("global/"), "global 不存在就不该有目录条目");
            assertNull(zipFile.getEntry("global/mappings.txt"));
            assertNull(zipFile.getEntry("other/c.json"));
        }
    }

    // 两个路径都不存在（repoRoot/global 与 mappingsFile 都没建出来）→ 没有可导出的东西：判失败，不留空包
    @Test
    void testPackFailsWhenBothPathsMissing() throws IOException {
        Path repo = repoRoot("repo");   // 什么都不写：global 与 mappings.txt 都不存在

        Path zip = tempDir.resolve("out.zip");
        PackReport report = packReport(repo, zip, newManifest());

        assertEquals(PackReport.outcome.IO_FAILED, report.outcome(), "两个路径都不存在时导出必须失败");
        assertEquals(0, report.fileCount());
        assertFalse(Files.exists(zip), "失败时不该在目标位置留下空包");
    }

    // 仓库里有文件、但一条都不在白名单里：等于两条来源都没有，照样判失败
    @Test
    void testPackFailsWhenOnlyExcludedFilesExist() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "other/c.json", "C");

        Path zip = tempDir.resolve("out.zip");
        PackReport report = packReport(repo, zip, newManifest());

        assertEquals(PackReport.outcome.IO_FAILED, report.outcome(), "白名单外的文件不算内容");
        assertEquals(0, report.fileCount());
        assertFalse(Files.exists(zip), "失败时不该在目标位置留下空包");
    }

    // global 在、但一个文件都没有（只剩空目录），mappings 也没有 → 同样是空包，一样判失败。
    // 这条同时压住两源重载：判定要落在"有没有文件进包"上，不是"两个路径存不存在"
    @Test
    void testPackFailsWhenNoFileEntersThePackage() throws IOException {
        Path repo = repoRoot("repo");
        Files.createDirectories(repo.resolve("global/sodium"));

        Path zip = tempDir.resolve("out.zip");
        assertEquals(PackReport.outcome.IO_FAILED,
                packTwo(repo, repo.resolve("mappings.txt"), zip).outcome(),
                "只有目录条目、没有一个配置文件时导出必须失败");
        assertFalse(Files.exists(zip));
    }

    // mappingsFile 在仓库之外也是同一条来源：条目名固定是包根部的 mappings.txt，与源文件名无关
    @Test
    void testPackTakesMappingsFromOutsideRepoRoot() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "global/a.json", "A");
        write(tempDir.resolve("elsewhere"), "mapping.txt", "ELSEWHERE");

        Path zip = tempDir.resolve("out.zip");
        PackReport report = assertDoesNotThrow((ThrowingSupplier<PackReport>)
                () -> RepoPacker.pack(repo, tempDir.resolve("elsewhere/mapping.txt"), zip, newManifest()));

        assertEquals(PackReport.outcome.SUCCESS, report.outcome());
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            assertEquals("ELSEWHERE", entryContent(zipFile, "mappings.txt"));
        }
    }

    // 打包失败不能留下可用的残包：输出打不开时目标位置什么也没有
    @Test
    void testFailedPackLeavesNoUsablePackage() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "global/a.json", "A");
        write(repo, "mappings.txt", "M");
        Path blocked = write(tempDir, "blocked", "not a directory");
        Path zip = blocked.resolve("out.zip");

        assertFalse(pack(repo, zip, newManifest()));

        assertFalse(Files.exists(zip), "失败的打包不该在目标位置留下包");
        assertFalse(unpack(newTarget(), zip), "目标位置没有包，导入要拒绝");
    }

    // ---------- 单根重载与两源等价 ----------

    // 默认布局（mappings.txt 就在 repoRoot 下）时，单根重载必须与"显式传 repoRoot/mappings.txt"是同一件事：
    // 重载只省一个参数，不能偷偷变成另一套行为
    @Test
    void testSingleRootOverloadsEqualTwoSourceWithDefaultLayout() throws IOException {
        Path repo = repoRoot("repo");
        write(repo, "global/a.json", "A");
        write(repo, "global/sub/b.json", "B");
        write(repo, RepoMetaStore.GLOBAL_ENTRY, "{\"format\":" + RepoMeta.FORMAT + ",\"mods\":{}}");
        write(repo, "mappings.txt", "M");

        Path viaOverload = tempDir.resolve("overload.zip");
        Path viaTwoSource = tempDir.resolve("two-source.zip");
        assertTrue(pack(repo, viaOverload, newManifest()));
        // 3 个文件 + 目录条目：global/、global/sub/ 不计入 fileCount
        assertEquals(4, packTwo(repo, repo.resolve("mappings.txt"), viaTwoSource).fileCount());

        // 同一批条目、同一份内容（含 global/meta.json，它随 global 走）
        assertEquals(entryNames(viaOverload), entryNames(viaTwoSource));
        assertTrue(entryNames(viaOverload).contains(RepoMetaStore.GLOBAL_ENTRY), "meta 应该随 global 一起进包");
        try (ZipFile a = new ZipFile(viaOverload.toFile()); ZipFile b = new ZipFile(viaTwoSource.toFile())) {
            for (String name : entryNames(viaOverload)) {
                if (!name.endsWith("/")) {
                    assertEquals(entryContent(a, name), entryContent(b, name), name);
                }
            }
        }

        // 解包落点也一致：单根重载等价于 mappingsFile 取 target/mappings.txt
        Path byOverload = newTarget();
        Path byTwoSource = newTarget();
        assertTrue(unpack(byOverload, viaOverload));
        assertEquals(PackReport.outcome.SUCCESS,
                unpackTwo(byTwoSource, byTwoSource.resolve("mappings.txt"), viaOverload).outcome());

        assertEquals(List.of("global/a.json", "global/meta.json", "global/sub/b.json", "mappings.txt"),
                relativeFiles(byOverload));
        assertEquals(relativeFiles(byOverload), relativeFiles(byTwoSource));
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

    // ---------- 解包：两条来源各自的落点与各自的 containment 根 ----------

    // 条目名正好是 mappings.txt → 落到 mappingsFile，仓库根里不出现同名文件；父级目录由解包端建出来
    @Test
    void testUnpackRoutesMappingsEntryToMappingsFile() throws IOException {
        Path target = newTarget();
        Path mappings = target.resolve("mapping/mapping.txt");
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/a.json", "A", "mappings.txt", "M");

        PackReport report = assertDoesNotThrow((ThrowingSupplier<PackReport>)
                () -> RepoPacker.unpack(target, mappings, zip));

        assertEquals(PackReport.outcome.SUCCESS, report.outcome());
        assertEquals(2, report.fileCount());
        assertEquals("A", read(target, "global/a.json"));
        assertEquals("M", Files.readString(mappings));
        assertFalse(Files.exists(target.resolve("mappings.txt")), "mappings.txt 不该解到仓库根里");
    }

    // ../mapping.txt 不是 mappings 路由（条目名不正好等于 mappings.txt）：按 repoRoot 的越界判定拒绝
    @Test
    void testUnpackRejectsMappingsLookalikeEscapingRepoRoot() throws IOException {
        Path target = newTarget();
        Path mappings = tempDir.resolve("elsewhere/mapping.txt");
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "../mapping.txt", "pwned");

        PackReport report = assertDoesNotThrow((ThrowingSupplier<PackReport>)
                () -> RepoPacker.unpack(target, mappings, zip));

        assertEquals(PackReport.outcome.CORRUPTED, report.outcome());
        assertFalse(Files.exists(tempDir.resolve("mapping.txt")), "越界文件被写出来了");
        assertFalse(Files.exists(mappings), "被拒绝的条目不该写到 mappingsFile");
        assertTrue(children(target).isEmpty());
    }

    // mappingsFile 的父级被普通文件占住：按 mappings 自己的根做父级校验才拦得住
    @Test
    void testUnpackRejectsMappingsBlockedByFileParent() throws IOException {
        Path target = newTarget();
        Path blocker = write(target, "mapping", "FILE");
        Path mappings = blocker.resolve("mapping.txt");
        Path zip = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "mappings.txt", "M");

        PackReport report = assertDoesNotThrow((ThrowingSupplier<PackReport>)
                () -> RepoPacker.unpack(target, mappings, zip));

        assertEquals(PackReport.outcome.IO_FAILED, report.outcome());
        assertEquals("FILE", Files.readString(blocker), "拒绝时不该动已有文件");
        assertEquals(List.of(blocker), children(target), "拒绝时不该多出任何文件");
    }

    // 两源共用一套计划路径（都要绝对化 + normalize 才比得出来）：跨源撞名两种排法都拒绝
    @Test
    void testUnpackRejectsMappingsConflictingWithFileEntry() throws IOException {
        Path mappingsFirst = newTarget();
        Path zipMappingsFirst = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST,
                "mappings.txt", "M", "mapping", "FILE");
        assertEquals(PackReport.outcome.CORRUPTED,
                assertDoesNotThrow((ThrowingSupplier<PackReport>)
                        () -> RepoPacker.unpack(mappingsFirst, mappingsFirst.resolve("mapping/mapping.txt"), zipMappingsFirst))
                        .outcome());
        assertTrue(children(mappingsFirst).isEmpty(), "拒绝时不该写出任何文件");

        Path fileFirst = newTarget();
        Path zipFileFirst = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST,
                "mapping", "FILE", "mappings.txt", "M");
        assertEquals(PackReport.outcome.CORRUPTED,
                assertDoesNotThrow((ThrowingSupplier<PackReport>)
                        () -> RepoPacker.unpack(fileFirst, fileFirst.resolve("mapping/mapping.txt"), zipFileFirst))
                        .outcome());
        assertTrue(children(fileFirst).isEmpty(), "拒绝时不该写出任何文件");
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

    // ---------- 来源记录 meta.json ----------

    // 仓库里的 global/meta.json 同样在白名单内：原样进包，导入端才能区分"包里带没带来源记录"
    @Test
    void testPackIncludesRepoMeta() throws IOException {
        Path repo = repoRoot("repo");
        String meta = "{\"format\":" + RepoMeta.FORMAT + ",\"mods\":{}}";
        write(repo, "mappings.txt", "M");
        write(repo, RepoMetaStore.GLOBAL_ENTRY, meta);

        Path zip = tempDir.resolve("out.zip");
        assertTrue(pack(repo, zip, newManifest()));

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            assertEquals(meta, entryContent(zipFile, RepoMetaStore.GLOBAL_ENTRY));
        }
    }

    // containsEntry：能读就明确回答有/没有；读不出来（不是 zip、文件不在）返回空，
    // 让调用方把"包里确实没带"与"不知道带没带"分开处理
    @Test
    void testContainsEntryDistinguishesAbsentFromUnreadable() throws IOException {
        Path withMeta = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, RepoMetaStore.GLOBAL_ENTRY, "{}");
        assertEquals(Optional.of(true), RepoPacker.containsEntry(withMeta, RepoMetaStore.GLOBAL_ENTRY));

        Path withoutMeta = zipOf(PackManifest.ENTRY_NAME, VALID_MANIFEST, "global/a.json", "A");
        assertEquals(Optional.of(false), RepoPacker.containsEntry(withoutMeta, RepoMetaStore.GLOBAL_ENTRY));

        Path notZip = write(tempDir, "plain.zip", "plain text");
        assertEquals(Optional.empty(), RepoPacker.containsEntry(notZip, RepoMetaStore.GLOBAL_ENTRY));
        assertEquals(Optional.empty(), RepoPacker.containsEntry(tempDir.resolve("no-such.zip"), RepoMetaStore.GLOBAL_ENTRY));
    }
}
