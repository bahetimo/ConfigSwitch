package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class RepoModScannerTest {
    @TempDir
    Path tempDir;

    private final RepoModScanner scanner = new RepoModScanner();

    // 在 common 下造一个 mod 目录，parent 不存在时一并创建
    private Path makeModDir(Path commonDir, String name) throws IOException {
        Path dir = commonDir.resolve(name);
        Files.createDirectories(dir);
        return dir;
    }

    // ---------- 基本：只收 common 下一层的目录名 ----------

    // 每个 mod 目录名进集合；返回的是名字而不是路径
    @Test
    void testScanReturnsDirectoryNames() throws IOException {
        Path commonDir = tempDir.resolve("common");
        makeModDir(commonDir, "sodium");
        makeModDir(commonDir, "appleskin");
        makeModDir(commonDir, "foo-bar");

        Set<String> modIds = scanner.scan(commonDir);

        assertEquals(Set.of("sodium", "appleskin", "foo-bar"), modIds);
    }

    // uncategorized/ 是分类器的落点目录，不是 modid，必须被排除
    @Test
    void testUncategorizedExcluded() throws IOException {
        Path commonDir = tempDir.resolve("common");
        makeModDir(commonDir, "sodium");
        makeModDir(commonDir, ModGroup.UNCATEGORIZED_ID);

        Set<String> modIds = scanner.scan(commonDir);

        assertEquals(Set.of("sodium"), modIds);
    }

    // ---------- 目录不可用时返回空集，不抛异常 ----------

    // 目录不存在（首次运行还没建）→ 空集，不抛
    @Test
    void testMissingDirectoryReturnsEmpty() {
        Path missing = tempDir.resolve("no-such-common");

        Set<String> modIds = assertDoesNotThrow(() -> scanner.scan(missing));

        assertTrue(modIds.isEmpty());
    }

    // null → 空集，不抛
    @Test
    void testNullReturnsEmpty() {
        Set<String> modIds = assertDoesNotThrow(() -> scanner.scan(null));

        assertTrue(modIds.isEmpty());
    }

    // ---------- 只取目录、不递归 ----------

    // 真实数据里 common/options.txt 就躺在那儿：它是【文件】，不能进集合
    // 这条是"只取目录"这个约束的唯一测试
    @Test
    void testFilesAreIgnored() throws IOException {
        Path commonDir = tempDir.resolve("common");
        Files.createDirectories(commonDir);
        Files.writeString(commonDir.resolve("options.txt"), "not a mod dir");
        makeModDir(commonDir, "sodium");

        Set<String> modIds = scanner.scan(commonDir);

        assertEquals(Set.of("sodium"), modIds);
    }

    // 确认不是递归：common/sodium/nested/ 只贡献 sodium，nested 不进集合
    @Test
    void testSubdirectoryNotRecursed() throws IOException {
        Path commonDir = tempDir.resolve("common");
        Files.createDirectories(commonDir.resolve("sodium/nested"));
        makeModDir(commonDir, "appleskin");

        Set<String> modIds = scanner.scan(commonDir);

        assertEquals(Set.of("sodium", "appleskin"), modIds);
    }
}
