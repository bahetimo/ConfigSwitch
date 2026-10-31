package com.bteamore.configswitch.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RepoMetaStoreTest {
    @TempDir
    Path tempDir;

    // 真实落点是 <repo>/global/meta.json，测试保留同样两层结构
    private Path metaPath() {
        return tempDir.resolve("global").resolve("meta.json");
    }

    private RepoMetaStore store() {
        return new RepoMetaStore(metaPath());
    }

    private void writeFile(String content) throws IOException {
        Files.createDirectories(metaPath().getParent());
        Files.writeString(metaPath(), content);
    }

    // meta.json 里声明的格式版本
    private static final int FORMAT = RepoMeta.FORMAT;

    // 记录写入时刻的 ISO-8601 UTC 时间
    private static final String AT = "2026-10-29T13:00:00Z";

    private ModSource mod(String minecraft, String loader) {
        return new ModSource(minecraft, loader, AT);
    }

    // 落点目录里现存的文件名（升序）：用来查临时文件残留
    private List<String> filesInGlobalDir() throws IOException {
        try (var entries = Files.list(metaPath().getParent())) {
            return entries.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    // ---------- 读：缺失与损坏一律空表，不抛 ----------

    // 文件不存在 → 空表，且不因读而创建文件
    @Test
    void testLoadMissingFileReturnsEmpty() {
        RepoMeta meta = store().load();

        assertEquals(Map.of(), meta.mods());
        assertFalse(Files.exists(metaPath()));
    }

    // 空文件（0 字节）→ 空表
    @Test
    void testLoadEmptyFileReturnsEmpty() throws IOException {
        writeFile("");

        assertEquals(Map.of(), store().load().mods());
    }

    // 完全不是 JSON → 空表，不把解析异常抛给调用方
    @Test
    void testLoadGarbageReturnsEmpty() throws IOException {
        writeFile("this is not json at all");

        assertEquals(Map.of(), store().load().mods());
    }

    // 顶层形状不对（数组而不是对象）→ 空表
    @Test
    void testLoadWrongTopLevelShapeReturnsEmpty() throws IOException {
        writeFile("[1, 2, 3]");

        assertEquals(Map.of(), store().load().mods());
    }

    // mods 不是对象 → 空表
    @Test
    void testLoadModsNotObjectReturnsEmpty() throws IOException {
        writeFile("{\"format\": 1, \"mods\": \"oops\"}");

        assertEquals(Map.of(), store().load().mods());
    }

    // 缺 mods 字段 → 空表（format 有值也不影响）
    @Test
    void testLoadMissingModsReturnsEmpty() throws IOException {
        writeFile("{\"format\": 1}");

        assertEquals(Map.of(), store().load().mods());
    }

    // 落点被目录占住（读必然失败）→ 空表，不抛
    @Test
    void testLoadOnUnreadablePathReturnsEmpty() throws IOException {
        Files.createDirectories(metaPath());

        assertEquals(Map.of(), store().load().mods());
    }

    // ---------- 读：正常内容 ----------

    // 规格里给的样例整份读出来：format 与三个字段都原样保留
    @Test
    void testLoadParsesSampleMetaFile() throws IOException {
        writeFile("""
                {
                  "format": 1,
                  "mods": {
                    "sodium": {
                      "minecraft": "1.21.1",
                      "loader": "fabric",
                      "at": "2026-10-29T13:00:00Z"
                    }
                  }
                }
                """);

        RepoMeta meta = store().load();

        assertEquals(FORMAT, meta.format());
        assertEquals(Map.of("sodium", mod("1.21.1", "fabric")), meta.mods());
    }

    // 多条 mod：key 是 modId，逐条独立读出
    @Test
    void testLoadKeepsEveryModEntry() throws IOException {
        writeFile("""
                {
                  "format": 1,
                  "mods": {
                    "sodium": {"minecraft": "1.21.1", "loader": "fabric", "at": "2026-10-29T13:00:00Z"},
                    "jei": {"minecraft": "1.20.1", "loader": "forge", "at": "2026-10-28T09:30:00Z"}
                  }
                }
                """);

        RepoMeta meta = store().load();

        assertEquals(Map.of(
                "sodium", mod("1.21.1", "fabric"),
                "jei", new ModSource("1.20.1", "forge", "2026-10-28T09:30:00Z")), meta.mods());
    }

    // ---------- 写 ----------

    // 落点目录不存在时由写入自己创建，写完能原样读回
    @Test
    void testSaveCreatesParentDirectories() {
        RepoMeta written = new RepoMeta(FORMAT, Map.of("sodium", mod("1.21.1", "fabric")));

        assertDoesNotThrow(() -> store().save(written));

        assertTrue(Files.exists(metaPath()));
        assertEquals(written, store().load());
    }

    // 落盘内容就是约定的 JSON 形状：format + mods.<modId>.{minecraft,loader,at}
    // 先去掉空白再断言：紧凑输出与美化输出都算合格，只钉住字段名与取值
    @Test
    void testSaveWritesExpectedJsonShape() throws IOException {
        assertDoesNotThrow(() -> store().save(
                new RepoMeta(FORMAT, Map.of("sodium", mod("1.21.1", "fabric")))));

        String compact = Files.readString(metaPath()).replaceAll("\\s", "");
        assertTrue(compact.contains("\"format\":" + FORMAT), compact);
        assertTrue(compact.contains("\"mods\""), compact);
        assertTrue(compact.contains("\"sodium\""), compact);
        assertTrue(compact.contains("\"minecraft\":\"1.21.1\""), compact);
        assertTrue(compact.contains("\"loader\":\"fabric\""), compact);
        assertTrue(compact.contains("\"at\":\"" + AT + "\""), compact);
    }

    // 已存在旧文件时被整份替换：不是追加，也不是两份内容并存
    @Test
    void testSaveReplacesExistingContent() throws IOException {
        assertDoesNotThrow(() -> store().save(new RepoMeta(FORMAT, Map.of("sodium", mod("1.21.1", "fabric")))));
        assertDoesNotThrow(() -> store().save(new RepoMeta(FORMAT, Map.of("jei", mod("1.20.1", "forge")))));

        assertEquals(Map.of("jei", mod("1.20.1", "forge")), store().load().mods());
        assertFalse(Files.readString(metaPath()).contains("sodium"));
    }

    // 原子替换的副产物：落点目录里只该有 meta.json，临时文件不能留下
    @Test
    void testSaveLeavesNoTempFileBehind() throws IOException {
        assertDoesNotThrow(() -> store().save(new RepoMeta(FORMAT, Map.of("sodium", mod("1.21.1", "fabric")))));

        assertEquals(List.of("meta.json"), filesInGlobalDir());
    }

    // 落点被同名目录占住（写必然失败）→ 不抛
    @Test
    void testSaveOnUnwritablePathDoesNotThrow() throws IOException {
        Files.createDirectories(metaPath());

        assertDoesNotThrow((ThrowingSupplier<Void>) () -> {
            store().save(new RepoMeta(FORMAT, Map.of("sodium", mod("1.21.1", "fabric"))));
            return null;
        });
    }

    // ---------- 改：读一次、改、写一次 ----------

    // 一次 update 写入多条：全部落盘，而不是只有最后一条（压"每个文件都读写"的退化实现）
    @Test
    void testUpdatePersistsEveryEntryInOneGo() {
        RepoMetaStore store = store();

        store.update(meta -> {
            meta.mods().put("sodium", mod("1.21.1", "fabric"));
            meta.mods().put("jei", mod("1.21.1", "fabric"));
            meta.mods().put("etf", mod("1.21.1", "fabric"));
        });

        assertEquals(Map.of(
                "sodium", mod("1.21.1", "fabric"),
                "jei", mod("1.21.1", "fabric"),
                "etf", mod("1.21.1", "fabric")), store().load().mods());
    }

    // 只改一个 mod 时其余条目不能被丢：读改写必须带上原有内容
    @Test
    void testUpdateKeepsUntouchedEntries() {
        RepoMetaStore store = store();
        store.save(new RepoMeta(FORMAT, Map.of(
                "sodium", mod("1.21.1", "fabric"),
                "jei", mod("1.20.1", "forge"))));

        store.update(meta -> meta.mods().put("jei", mod("1.21.1", "fabric")));

        assertEquals(Map.of(
                "sodium", mod("1.21.1", "fabric"),
                "jei", mod("1.21.1", "fabric")), store().load().mods());
    }

    // 文件已损坏时 update 从空表起步继续写，不因读失败而整体放弃
    @Test
    void testUpdateOnCorruptFileStartsFromEmpty() throws IOException {
        writeFile("not json");

        RepoMetaStore store = store();
        assertDoesNotThrow(() -> store.update(meta -> meta.mods().put("sodium", mod("1.21.1", "fabric"))));

        assertEquals(Map.of("sodium", mod("1.21.1", "fabric")), store().load().mods());
    }

    // 传空改动也要能正常走完：内容保持不变
    @Test
    void testUpdateWithoutChangesKeepsContent() {
        RepoMetaStore store = store();
        RepoMeta written = new RepoMeta(FORMAT, Map.of("sodium", mod("1.21.1", "fabric")));
        store.save(written);

        assertDoesNotThrow(() -> store.update(meta -> { }));

        assertEquals(written.mods(), store().load().mods());
    }

    // ---------- 往返 ----------

    // save 后由全新实例 load：format 与每条 ModSource 的三个字段全部一致
    @Test
    void testSaveLoadRoundTrip() {
        RepoMeta written = new RepoMeta(FORMAT, Map.of(
                "sodium", new ModSource("1.21.1", "fabric", "2026-10-29T13:00:00Z"),
                "jei", new ModSource("1.20.1", "forge", "2026-10-28T09:30:00Z")));

        assertDoesNotThrow(() -> store().save(written));

        assertEquals(written, store().load());
    }
}
