package com.bteamore.configswitch.config;

import com.bteamore.configswitch.repo.RepoPaths;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModSettingsTest {
    @TempDir
    Path tempDir;

    // 归一化后的默认值：RepoPaths.root() + 10
    private static final String DEFAULT_REPO_ROOT = RepoPaths.root().toString();
    private static final String CUSTOM_REPO_ROOT = "D:/custom-repo";

    // ---------- 合法输入原样保留 ----------

    // 自定义路径与备份数都保留
    @Test
    void testKeepsProvidedValues() {
        ModSettings settings = new ModSettings(CUSTOM_REPO_ROOT, 5);

        assertEquals(CUSTOM_REPO_ROOT, settings.repoRoot());
        assertEquals(5, settings.maxBackupCount());
    }

    // 1 是合法值，不会被当成非法值替换掉
    @Test
    void testKeepsSmallestPositiveBackupCount() {
        assertEquals(1, new ModSettings(CUSTOM_REPO_ROOT, 1).maxBackupCount());
    }

    // ---------- 归一化：repoRoot ----------

    // null → RepoPaths.root()
    @Test
    void testNullRepoRootFallsBackToDefault() {
        assertEquals(DEFAULT_REPO_ROOT, new ModSettings(null, 5).repoRoot());
    }

    // 空串 / 全空白 → RepoPaths.root()
    @Test
    void testBlankRepoRootFallsBackToDefault() {
        assertEquals(DEFAULT_REPO_ROOT, new ModSettings("", 5).repoRoot());
        assertEquals(DEFAULT_REPO_ROOT, new ModSettings("   ", 5).repoRoot());
    }

    // ---------- 归一化：maxBackupCount ----------

    // 0 / 负数 → 10
    @Test
    void testNonPositiveBackupCountFallsBackToTen() {
        assertEquals(10, new ModSettings(CUSTOM_REPO_ROOT, 0).maxBackupCount());
        assertEquals(10, new ModSettings(CUSTOM_REPO_ROOT, -3).maxBackupCount());
    }

    // 两个字段各自独立归一化
    @Test
    void testBothFieldsNormalizedIndependently() {
        ModSettings settings = new ModSettings(null, -1);

        assertEquals(DEFAULT_REPO_ROOT, settings.repoRoot());
        assertEquals(10, settings.maxBackupCount());
    }

    // record 语义：同值相等
    @Test
    void testValueEquality() {
        assertEquals(new ModSettings(CUSTOM_REPO_ROOT, 5), new ModSettings(CUSTOM_REPO_ROOT, 5));
        assertNotEquals(new ModSettings(CUSTOM_REPO_ROOT, 5), new ModSettings(CUSTOM_REPO_ROOT, 6));
    }

    // ---------- 默认常量 ----------

    // DEFAULT 就是“归一后”的默认值：RepoPaths.root() + 10（SettingsStore 落回默认值时用它）
    @Test
    void testDefaultConstantMatchesDefaults() {
        assertEquals(DEFAULT_REPO_ROOT, ModSettings.DEFAULT.repoRoot());
        assertEquals(10, ModSettings.DEFAULT.maxBackupCount());
        assertEquals(new ModSettings(DEFAULT_REPO_ROOT, 10), ModSettings.DEFAULT);
    }

    // ---------- 落盘格式（Gson） ----------

    // JSON 往返保留自定义值
    @Test
    void testGsonRoundTripKeepsCustomValues() {
        Gson gson = new Gson();
        ModSettings original = new ModSettings(CUSTOM_REPO_ROOT, 5);

        ModSettings restored = gson.fromJson(gson.toJson(original), ModSettings.class);

        assertEquals(original, restored);
    }

    // 反序列化走规范化构造器：文件里的 null / 非法值同样被归一化
    @Test
    void testGsonDeserializationNormalizesInvalidValues() {
        ModSettings loaded = new Gson().fromJson("{\"repoRoot\":null,\"maxBackupCount\":0}", ModSettings.class);

        assertEquals(DEFAULT_REPO_ROOT, loaded.repoRoot());
        assertEquals(10, loaded.maxBackupCount());
    }

    // 未知字段被忽略：以后给配置文件加字段不会让旧文件加载失败
    @Test
    void testGsonIgnoresUnknownFields() {
        ModSettings loaded = new Gson().fromJson(
                "{\"repoRoot\":\"D:/custom-repo\",\"maxBackupCount\":5,\"futureField\":true}", ModSettings.class);

        assertEquals(new ModSettings(CUSTOM_REPO_ROOT, 5), loaded);
    }

    // ---------- validateRepoRoot ----------
    // 注意：校验失败时返回的是【翻译 key】而不是文案——因为它在 main 层，不能依赖 MC 的 Text，
    // 由 UI 层调用 Text.translatable(key) 显示。文案见 assets/configswitch/lang/。

    // null / 空串 / 全空白 → 返回提示而不是 null
    @Test
    void testValidateRejectsBlankPath() {
        assertEquals("configswitch.error.path_blank", ModSettings.validateRepoRoot(null));
        assertEquals("configswitch.error.path_blank", ModSettings.validateRepoRoot(""));
        assertEquals("configswitch.error.path_blank", ModSettings.validateRepoRoot("   "));
    }

    // 相对路径 → 直接提示要完整路径，不落到后面的存在性检查上
    @Test
    void testValidateRejectsRelativePath() {
        assertEquals("configswitch.error.path_relative", ModSettings.validateRepoRoot("relative-repo"));
        assertEquals("configswitch.error.path_relative", ModSettings.validateRepoRoot("nested" + "\\" + "deep"));
    }

    // 合法但还不存在的路径 → 返回 null；只校验不创建，目录不该被建出来
    @Test
    void testValidateAcceptsMissingPathWithoutCreating() {
        Path nested = tempDir.resolve("nested").resolve("deep").resolve("repo");

        assertNull(ModSettings.validateRepoRoot(nested.toString()));
        assertFalse(Files.exists(tempDir.resolve("nested")), "校验不应创建目录");
    }

    // 已存在且可写的目录 → 返回 null，可以重复校验（GUI 里回车/失焦都调一次也不会报错）
    @Test
    void testValidateIsRepeatableOnExistingDir() throws IOException {
        Path existing = tempDir.resolve("repo");
        Files.createDirectories(existing);

        assertNull(ModSettings.validateRepoRoot(existing.toString()));
        assertNull(ModSettings.validateRepoRoot(existing.toString()));
    }

    // 最近的已存在目录不可写 → 单独提示，而不是放过校验
    @Test
    void testValidateRejectsUnwritableDir() throws IOException {
        Path locked = tempDir.resolve("locked-repo");
        Files.createDirectories(locked);
        // Windows 的可写判定走 ACL（DOS 只读属性对目录不生效），所以加一条 DENY 写权限的 ACE
        AclFileAttributeView aclView = Files.getFileAttributeView(locked, AclFileAttributeView.class);
        UserPrincipal me = locked.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        List<AclEntry> original = new ArrayList<>(aclView.getAcl());
        List<AclEntry> denied = new ArrayList<>(original);
        denied.add(0, AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(me)
                .setPermissions(EnumSet.of(AclEntryPermission.WRITE_DATA))
                .build());
        aclView.setAcl(denied);
        try {
            // 落点本身 / 落点还不存在（用户输入 Program Files 下的新目录）都要挡住
            assertEquals("configswitch.error.path_unwritable", ModSettings.validateRepoRoot(locked.toString()));
            assertEquals("configswitch.error.path_unwritable", ModSettings.validateRepoRoot(locked.resolve("sub").toString()));
        } finally {
            // 撤掉 ACE，别影响 @TempDir 清理
            aclView.setAcl(original);
        }
    }

    // 落点被同名普通文件占位 / 上级是普通文件 → 各自给出提示，不抛异常
    @Test
    void testValidateRejectsPathOccupiedByFile() throws IOException {
        Path file = tempDir.resolve("not-a-dir");
        Files.writeString(file, "not a directory");

        assertEquals("configswitch.error.path_is_file", ModSettings.validateRepoRoot(file.toString()));
        assertEquals("configswitch.error.parent_not_dir", ModSettings.validateRepoRoot(file.resolve("repo").toString()));
    }

    // 语法异常（Windows 下 * 等、以及 NUL）→ 单独一类提示，而不是抛 InvalidPathException
    @Test
    void testValidateRejectsIllegalPathCharacters() {
        // 字符串必须拼出来：tempDir.resolve("in*valid") 自己就会在解析阶段抛 InvalidPathException
        List<String> illegalPaths = List.of(tempDir + "\\in*valid", tempDir + "\\nul\u0000char");

        for (String illegal : illegalPaths) {
            assertEquals("configswitch.error.path_illegal", ModSettings.validateRepoRoot(illegal));
        }
    }

    // 访问异常（单级目录名超出文件系统上限）→ 与语法异常不同的提示
    @Test
    void testValidateReportsInaccessiblePath() {
        String tooLong = tempDir.resolve("a".repeat(300)).toString();

        assertEquals("configswitch.error.path_inaccessible", ModSettings.validateRepoRoot(tooLong));
    }
}
