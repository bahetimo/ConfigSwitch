package com.bteamore.configswitch.client.gui.widget;

import com.bteamore.configswitch.core.PackReport;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TransferPanel 的纯规则：拖入挑包、提示与结果的文案 key、能否亮出「打开文件夹」；
 * 外加一条资源检查：两个语言文件里都要有这块面板用到的 key，漏配就在这里红。
 * 面板本体要 Minecraft，装配与绘制不在这里测（跟 ConfigIconButton 一样只钉规则）。
 */
public class TransferPanelTest {

    @TempDir
    Path tempDir;

    // ---------- 拖入挑包 ----------

    // 单个 .zip 普通文件：通过，并把路径原样带出
    @Test
    void testPickAcceptsSingleZipFile() throws IOException {
        Path zip = tempDir.resolve("2026-01-01--00-00-00-export.zip");
        Files.writeString(zip, "zip-bytes");

        TransferPanel.Drop drop = TransferPanel.pick(List.of(zip));

        assertEquals(TransferPanel.Reason.OK, drop.reason());
        assertEquals(zip, drop.packagePath());
    }

    // 什么都没拖进来：空列表与 null 都算「没拖文件」
    @Test
    void testPickRejectsEmptyDrop() {
        TransferPanel.Drop fromEmpty = TransferPanel.pick(List.of());
        TransferPanel.Drop fromNull = TransferPanel.pick(null);

        assertEquals(TransferPanel.Reason.EMPTY, fromEmpty.reason());
        assertEquals(TransferPanel.Reason.EMPTY, fromNull.reason());
        assertNull(fromNull.packagePath());
    }

    // 一次拖多个：不猜先导哪个，直接拒绝
    @Test
    void testPickRejectsMultipleFiles() throws IOException {
        Path first = tempDir.resolve("a.zip");
        Path second = tempDir.resolve("b.zip");
        Files.writeString(first, "a");
        Files.writeString(second, "b");

        TransferPanel.Drop drop = TransferPanel.pick(List.of(first, second));

        assertEquals(TransferPanel.Reason.MULTIPLE, drop.reason());
        assertNull(drop.packagePath());
    }

    // 目录不是包：拖文件夹进来给「只能导入文件」
    @Test
    void testPickRejectsDirectory() throws IOException {
        Path dir = Files.createDirectory(tempDir.resolve("repo"));

        assertEquals(TransferPanel.Reason.NOT_A_FILE, TransferPanel.pick(List.of(dir)).reason());
    }

    // 后缀不对：.txt 与「.zip 只出现在中间」的名字都不收
    @Test
    void testPickRejectsNonZipSuffix() throws IOException {
        Path txt = tempDir.resolve("pack.txt");
        Path tricky = tempDir.resolve("pack.zip.txt");
        Files.writeString(txt, "x");
        Files.writeString(tricky, "x");

        assertEquals(TransferPanel.Reason.WRONG_EXTENSION, TransferPanel.pick(List.of(txt)).reason());
        assertEquals(TransferPanel.Reason.WRONG_EXTENSION, TransferPanel.pick(List.of(tricky)).reason());
    }

    // 后缀大小写不敏感：.ZIP 也认（包里真不真由 manifest 校验把关）
    @Test
    void testPickMatchesSuffixIgnoringCase() throws IOException {
        Path upper = tempDir.resolve("PACK.ZIP");
        Files.writeString(upper, "x");

        assertEquals(TransferPanel.Reason.OK, TransferPanel.pick(List.of(upper)).reason());
    }

    // ---------- 提示与结果的文案 key ----------

    // 四种拒绝原因各有各的 key，不能串
    @Test
    void testHintKeysAreDistinctPerReason() {
        assertEquals("configswitch.transfer.nothing_dropped", TransferPanel.hintKey(TransferPanel.Reason.EMPTY));
        assertEquals("configswitch.transfer.not_a_file", TransferPanel.hintKey(TransferPanel.Reason.NOT_A_FILE));
        assertEquals("configswitch.transfer.wrong_extension", TransferPanel.hintKey(TransferPanel.Reason.WRONG_EXTENSION));
        assertEquals("configswitch.transfer.multiple_files", TransferPanel.hintKey(TransferPanel.Reason.MULTIPLE));
    }

    // 成功文案按方向分：导入与导出不能共用一句
    @Test
    void testSuccessKeyFollowsDirection() {
        assertEquals("configswitch.transfer.import_done",
                TransferPanel.resultKey(TransferPanel.Kind.IMPORT, PackReport.outcome.SUCCESS));
        assertEquals("configswitch.transfer.export_done",
                TransferPanel.resultKey(TransferPanel.Kind.EXPORT, PackReport.outcome.SUCCESS));
    }

    // 三种失败文案与方向无关：包坏了、读写失败都各只有一句
    @Test
    void testFailureKeysIgnoreDirection() {
        assertEquals("configswitch.transfer.not_a_package",
                TransferPanel.resultKey(TransferPanel.Kind.IMPORT, PackReport.outcome.NOT_A_PACKAGE));
        assertEquals("configswitch.transfer.not_a_package",
                TransferPanel.resultKey(TransferPanel.Kind.EXPORT, PackReport.outcome.NOT_A_PACKAGE));
        assertEquals("configswitch.transfer.corrupted",
                TransferPanel.resultKey(TransferPanel.Kind.IMPORT, PackReport.outcome.CORRUPTED));
        assertEquals("configswitch.transfer.io_failed",
                TransferPanel.resultKey(TransferPanel.Kind.EXPORT, PackReport.outcome.IO_FAILED));
    }

    // ---------- 能否亮出「打开文件夹」 ----------

    // 只有成功、且目标确实是存在的目录才亮：原版 open 失败只写日志，亮出来点了也不会有反应
    @Test
    void testCanOpenFolderOnlyForSuccessAndExistingDirectory() throws IOException {
        Path repo = Files.createDirectory(tempDir.resolve("repo"));
        Path zip = tempDir.resolve("pack.zip");
        Files.writeString(zip, "x");

        assertTrue(TransferPanel.canOpenFolder(report(PackReport.outcome.SUCCESS, zip), repo));
        // 目标是文件而不是目录
        assertFalse(TransferPanel.canOpenFolder(report(PackReport.outcome.SUCCESS, zip), zip));
        // 目标不存在
        assertFalse(TransferPanel.canOpenFolder(report(PackReport.outcome.SUCCESS, zip), tempDir.resolve("nope")));
        // 压根没有目标
        assertFalse(TransferPanel.canOpenFolder(report(PackReport.outcome.SUCCESS, zip), null));
        // 没成功
        assertFalse(TransferPanel.canOpenFolder(report(PackReport.outcome.IO_FAILED, zip), repo));
        // 连报告都没有
        assertFalse(TransferPanel.canOpenFolder(null, repo));
    }

    // ---------- 语言文件里真的有这些 key ----------

    // 面板上出现的每个 key 都要在 en_us 与 zh_cn 里配好，漏一条这里就红
    @Test
    void testLanguageFilesCoverPanelKeys() throws IOException {
        String[] keys = {
                "configswitch.button.cancel_import",
                "configswitch.button.close",
                "configswitch.button.open_folder",
                "configswitch.transfer.hint_drop",
                "configswitch.transfer.reading",
                "configswitch.transfer.nothing_dropped",
                "configswitch.transfer.not_a_file",
                "configswitch.transfer.wrong_extension",
                "configswitch.transfer.multiple_files",
                "configswitch.transfer.import_done",
                "configswitch.transfer.export_done",
                "configswitch.transfer.not_a_package",
                "configswitch.transfer.corrupted",
                "configswitch.transfer.io_failed"
        };

        assertLangCovers("en_us", keys);
        assertLangCovers("zh_cn", keys);
    }

    private static PackReport report(PackReport.outcome outcome, Path location) {
        return new PackReport(outcome, 2, location);
    }

    private static void assertLangCovers(String locale, String[] keys) throws IOException {
        JsonObject lang = readLang(locale);
        for (String key : keys) {
            assertTrue(lang.has(key), locale + " 里缺少 " + key);
        }
    }

    // 语言文件跟着主源集打包，这里按 classpath 取，路径写错就等于漏配
    private static JsonObject readLang(String locale) throws IOException {
        String path = "/assets/configswitch/lang/" + locale + ".json";
        try (InputStream in = TransferPanelTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
    }
}
