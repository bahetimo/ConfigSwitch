package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.util.Hash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PendingRewrites.flush() 的行为契约（退出时重放 fetch，修"mod 用内存旧值抹掉 fetch"的 Bug）：
 * 仅当目标文件内容仍等于 fetch 前的旧内容（hashBeforeFetch）时，才把 source（全局仓库侧）
 * 按 source → target 方向写回；否则（已是 NEW / 已是第三种内容）不动。
 * 注意：INSTANCE 是静态列表、跨测试方法共享且无清空入口，故每个用例只用自己创建的文件断言，
 * 内容串全局唯一，历史条目的残留不会干扰（其目标目录已随 @TempDir 清理，哈希必然不匹配）。
 */
public class PendingRewritesTest {
    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    // ① 目标 == NEW（source 的内容）：mod 没有用旧值回写 → 不满足重写条件，文件不变
    @Test
    void testTargetEqualsNewKeepsFileUnchanged() throws IOException {
        String oldContent = "OLD-content-state-1";
        String newContent = "NEW-content-state-1";
        Path source = write("source-1.json", newContent);

        // 完整模拟真实时序：fetch 前 target 是旧值，fetch 后 target 变为 NEW
        Path target = write("target-1.json", oldContent);
        int hashBeforeFetch = Hash.hashOf(target); // fetch 时记录（此时是旧值）
        Files.writeString(target, newContent);     // fetch：global → active

        PendingRewrites.add(target, source, hashBeforeFetch);
        PendingRewrites.flush();

        // 当前内容 != hashBeforeFetch（旧值）→ 已是最新，不应被改动
        assertEquals(newContent, Files.readString(target));
        assertEquals(newContent, Files.readString(source));
    }

    // ② 目标 == OLD（== hashBeforeFetch）：mod 退出时把内存旧值写回 → 重放 fetch
    @Test
    void testTargetRevertedToOldGetsRewrittenFromSource() throws IOException {
        String oldContent = "OLD-content-state-2";
        String newContent = "NEW-content-state-2";
        Path source = write("source-2.json", newContent);

        // 时序：fetch 前旧值 → fetch 后 NEW → 退出时 mod 用内存旧值覆盖回 OLD
        Path target = write("target-2.json", oldContent);
        int hashBeforeFetch = Hash.hashOf(target);
        Files.writeString(target, newContent); // fetch
        Files.writeString(target, oldContent); // mod 退出回写（触发场景）

        PendingRewrites.add(target, source, hashBeforeFetch);
        PendingRewrites.flush();

        // 命中条件：目标被重写为 source 的完整内容（不是空的、不是截断的）
        assertEquals(newContent, Files.readString(target));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(target));
        // 复制方向 source → target：source 必须原样保留
        assertEquals(newContent, Files.readString(source));
        // 临时文件不残留（temp → 原子替换的收尾）
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    // ③ 目标 == 第三种内容：用户游戏内改过配置、mod 保存的是新值 → 不满足重写条件，文件不变
    @Test
    void testTargetEqualsThirdContentKeepsFileUnchanged() throws IOException {
        String oldContent = "OLD-content-state-3";
        String newContent = "NEW-content-state-3";
        String thirdContent = "USER-changed-content-state-3";
        Path source = write("source-3.json", newContent);

        Path target = write("target-3.json", oldContent);
        int hashBeforeFetch = Hash.hashOf(target);
        Files.writeString(target, newContent);   // fetch
        Files.writeString(target, thirdContent); // 退出：mod 保存的是游戏内改过的新值

        PendingRewrites.add(target, source, hashBeforeFetch);
        PendingRewrites.flush();

        // 既不是 OLD 也不是 NEW → 尊重用户改动，不重写
        assertEquals(thirdContent, Files.readString(target));
        assertEquals(newContent, Files.readString(source));
    }
}
