package com.bteamore.configswitch.repo;

import com.bteamore.configswitch.discovery.ModGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// LocalLayout 是"本地文件该落到哪"的唯一出处：active 位置、本地备份根、恢复落点、相对路径都从这里算。
// 每个用例都同时跑默认布局与自定义布局，避免规则被写死成 gameDir/config
public class LocalLayoutTest {
    @TempDir
    Path gameDir;

    // 刻意不用 gameDir/config：默认值相同就分不清"跟着布局走"还是"硬编码了 config"
    private static final String CUSTOM_CONFIG_DIR = "custom-config";

    // 默认布局：MC 的 config 目录就是 gameDir/config
    private LocalLayout defaultLayout() {
        return new LocalLayout(gameDir, gameDir.resolve("config"));
    }

    // 自定义布局：config 目录换了位置，其余规则应完全一致
    private LocalLayout customLayout() {
        return new LocalLayout(gameDir, gameDir.resolve(CUSTOM_CONFIG_DIR));
    }

    // ---------- activeFor ----------

    // 原版组（options.txt）：落在 gameDir 根，不带 config 前缀
    @Test
    void testActiveForVanillaLandsInGameDirRoot() {
        assertEquals(gameDir.resolve("options.txt"),
                defaultLayout().activeFor(ModGroup.VANILLA_ID, "options.txt"));
        // 组 id 的字面量同样走 gameDir；常量被改值时这行会先红
        assertEquals("minecraft", ModGroup.VANILLA_ID);
        assertEquals(gameDir.resolve("options.txt"),
                defaultLayout().activeFor("minecraft", "options.txt"));
    }

    // 普通 mod：落在 configDir 下
    @Test
    void testActiveForModLandsInConfigDir() {
        assertEquals(gameDir.resolve("config").resolve("sodium-options.json"),
                defaultLayout().activeFor("sodium", "sodium-options.json"));
    }

    // 相对路径带子目录时原样接在根后面
    @Test
    void testActiveForKeepsNestedRelativePath() {
        assertEquals(gameDir.resolve("config").resolve("sub/deep/x.json"),
                defaultLayout().activeFor("sodium", "sub/deep/x.json"));
    }

    // 自定义 configDir：mod 跟 configDir 走，原版仍在 gameDir 根
    @Test
    void testActiveForFollowsCustomConfigDir() {
        LocalLayout layout = customLayout();

        assertEquals(gameDir.resolve(CUSTOM_CONFIG_DIR).resolve("sodium-options.json"),
                layout.activeFor("sodium", "sodium-options.json"));
        assertEquals(gameDir.resolve("options.txt"),
                layout.activeFor(ModGroup.VANILLA_ID, "options.txt"));
    }

    // ---------- localBackupRoot ----------

    // 本地备份根固定在 gameDir/config/switch/backup
    @Test
    void testLocalBackupRootUnderGameDir() {
        assertEquals(gameDir.resolve("config").resolve("switch").resolve("backup"),
                defaultLayout().localBackupRoot());
    }

    // 备份根不跟 configDir 走：自定义 configDir 时它仍在 gameDir/config/switch/backup
    @Test
    void testLocalBackupRootIgnoresCustomConfigDir() {
        assertEquals(gameDir.resolve("config").resolve("switch").resolve("backup"),
                customLayout().localBackupRoot());
    }

    // ---------- localRestoreTarget ----------

    // 快照里的 config/<file> → configDir/<file>：config 前缀是分组标记，不能落成目录
    @Test
    void testLocalRestoreTargetStripsConfigPrefix() {
        assertEquals(gameDir.resolve("config").resolve("sodium-options.json"),
                defaultLayout().localRestoreTarget(Path.of("config", "sodium-options.json")));
    }

    // 快照里的 config/jei/jei.json → configDir/jei/jei.json：只脱掉分组前缀，mod 子目录原样保留
    @Test
    void testLocalRestoreTargetKeepsModSubdirectory() {
        assertEquals(gameDir.resolve("config").resolve("jei/jei.json"),
                defaultLayout().localRestoreTarget(Path.of("config", "jei", "jei.json")));
    }

    // 更深的层级同样只脱首段 config
    @Test
    void testLocalRestoreTargetKeepsNestedAfterConfig() {
        assertEquals(gameDir.resolve("config").resolve("sub/deep/x.json"),
                defaultLayout().localRestoreTarget(Path.of("config", "sub", "deep", "x.json")));
    }

    // 无 config 前缀的（options.txt）→ gameDir 根
    @Test
    void testLocalRestoreTargetKeepsVanillaInGameDir() {
        assertEquals(gameDir.resolve("options.txt"),
                defaultLayout().localRestoreTarget(Path.of("options.txt")));
    }

    // 只是名字以 config 开头的目录不算 config 前缀：configs/x.json 必须整段保留
    @Test
    void testLocalRestoreTargetOnlyMatchesWholeConfigSegment() {
        assertEquals(gameDir.resolve("configs").resolve("x.json"),
                defaultLayout().localRestoreTarget(Path.of("configs", "x.json")));
    }

    // 自定义 configDir：带 config 前缀的落点是 configDir（含 mod 子目录），而不是 gameDir/config
    @Test
    void testLocalRestoreTargetFollowsCustomConfigDir() {
        assertEquals(gameDir.resolve(CUSTOM_CONFIG_DIR).resolve("jei/jei.json"),
                customLayout().localRestoreTarget(Path.of("config", "jei", "jei.json")));
    }

    // ---------- relativeOf ----------

    // configDir 下的文件 → 相对 configDir 的路径（ConfigPathResolver 再用 activeFor 还原回绝对路径）
    @Test
    void testRelativeOfFileUnderConfigDirIsRelativeToConfigDir() {
        assertEquals("jei/jei.json",
                defaultLayout().relativeOf(gameDir.resolve("config").resolve("jei/jei.json")));
    }

    // 子目录保留，且分隔符统一成 /：Windows 上的 \ 不能漏进 global 仓库的路径里
    @Test
    void testRelativeOfNestedUsesForwardSlash() {
        assertEquals("sub/deep/x.json",
                defaultLayout().relativeOf(gameDir.resolve("config").resolve("sub/deep/x.json")));
    }

    // configDir 之外的文件 → 只取文件名（options.txt 就在 gameDir 根）
    @Test
    void testRelativeOfVanillaIsJustFileName() {
        assertEquals("options.txt", defaultLayout().relativeOf(gameDir.resolve("options.txt")));
    }

    // 自定义 configDir 同样按 configDir 取相对路径
    @Test
    void testRelativeOfFollowsCustomConfigDir() {
        assertEquals("sodium-options.json",
                customLayout().relativeOf(gameDir.resolve(CUSTOM_CONFIG_DIR).resolve("sodium-options.json")));
    }

    // ---------- 往返 ----------

    // ConfigScreen 的实际调用形态：relativeOf(绝对路径) 交给 ConfigPathResolver，
    // 后者用 activeFor 还原 —— 必须还原出同一个文件，否则 push 会读写错位置
    @Test
    void testRelativeOfRoundTripsThroughActiveFor() {
        LocalLayout layout = customLayout();
        Path modFile = gameDir.resolve(CUSTOM_CONFIG_DIR).resolve("jei/jei.json");
        Path vanillaFile = gameDir.resolve("options.txt");

        assertEquals(modFile, layout.activeFor("sodium", layout.relativeOf(modFile)));
        assertEquals(vanillaFile, layout.activeFor(ModGroup.VANILLA_ID, layout.relativeOf(vanillaFile)));
    }
}
