package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SubdirAnalyzerTest {
    @TempDir
    Path tempDir;

    // ---------- 造树与断言辅助 ----------

    private Path configRoot() throws IOException {
        return Files.createDirectories(tempDir.resolve("config"));
    }

    // 造文件（中间目录自动补齐），内容无关紧要
    private static void file(Path root, String relativeFile) throws IOException {
        Path file = root.resolve(relativeFile);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");
    }

    // 造空目录（中间目录自动补齐），用来验证空文件夹不进报告
    private static void dir(Path root, String relativeDir) throws IOException {
        Files.createDirectories(root.resolve(relativeDir));
    }

    private static List<String> pathsOf(SubdirReport report) {
        return report.entries().stream().map(SubdirReport.Entry::relativePath).toList();
    }

    // 报告顺序不做约定，所以一律按相对路径查找，不按下标
    private static SubdirReport.Entry at(SubdirReport report, String relativePath) {
        return report.entries().stream()
                .filter(entry -> entry.relativePath().equals(relativePath))
                .findFirst()
                .orElseThrow(() -> new AssertionError("报告缺少 " + relativePath + "，实际有 " + pathsOf(report)));
    }

    // 报告里有且只有这一条
    private static SubdirReport.Entry only(SubdirReport report, String relativePath) {
        assertEquals(List.of(relativePath), pathsOf(report));
        return at(report, relativePath);
    }

    // 报告里每条路径只该出现一次，所以排序后逐条比对（用 Set 会把重复悄悄吃掉）
    private static void assertPaths(SubdirReport report, String... expected) {
        assertEquals(Stream.of(expected).sorted().toList(), pathsOf(report).stream().sorted().toList(), "报告的条目");
    }

    // verdict + 命中的规则
    private static SubdirReport.Entry assertVerdict(SubdirReport report, String relativePath, SubdirReport.Verdict verdict, SubdirReport.Reason reason) {
        SubdirReport.Entry entry = at(report, relativePath);
        assertEquals(verdict, entry.verdict(), relativePath + " 的 verdict");
        assertEquals(reason, entry.reason(), relativePath + " 的 reason");
        return entry;
    }

    // 命中的依据（具体文件名 / 目录名）要跟着报告出来
    private static void assertDetail(SubdirReport report, String relativePath, String expectedDetail) {
        assertEquals(expectedDetail, at(report, relativePath).detail(), relativePath + " 的 detail");
    }

    // ---------- 骨架：config 下没有子目录 → 空报告 ----------

    @Test
    void testEmptyConfigDirProducesEmptyReport() throws IOException {
        Path config = configRoot();
        // 顶层扁平文件不归子目录分析管（那是 ConfigScanner 的活）
        file(config, "alphamod.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of("alphamod"));

        assertEquals(List.of(), pathsOf(report));
    }

    // ---------- 段黑名单目录：整棵跳过 ----------

    @Test
    void testBlacklistedSubdirSkipped() throws IOException {
        Path config = configRoot();
        file(config, "cache/x.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        only(report, "cache");
        assertVerdict(report, "cache", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.BLACKLIST);
        assertDetail(report, "cache", "cache");
    }

    // ---------- 空的黑名单目录不记账 ----------

    @Test
    void testEmptyBlacklistedDirIsNotReported() throws IOException {
        Path config = configRoot();
        dir(config, "cache");
        file(config, "alphamod/notes.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        only(report, "alphamod");
        assertVerdict(report, "alphamod", SubdirReport.Verdict.UNRECOGNIZED, SubdirReport.Reason.NONE);
    }

    // ---------- 扁平已找到：整棵跳过，且不再往里走 ----------

    @Test
    void testFlatFoundSubdirSkippedWithoutDescending() throws IOException {
        Path config = configRoot();
        file(config, "alphamod.json");
        file(config, "alphamod/x.json");
        // 子树里放一个子目录：它若被访问会单独入账，用来验证剪枝没有下钻
        file(config, "alphamod/betamod/notes.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of("alphamod"));

        only(report, "alphamod");
        assertVerdict(report, "alphamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.FLAT_FOUND);
        assertDetail(report, "alphamod", "alphamod");
    }

    // ---------- 本层有配置味文件 → 认领 ----------

    // config.json 不含目录名、目录名也造不出缩写 → 命中只可能来自 FLAVORS 词 config
    @Test
    void testOwnConfigLikeFileAdoptsDir() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/config.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        only(report, "alphamod");
        assertVerdict(report, "alphamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.FLAVOR);
        assertDetail(report, "alphamod", "config.json");
    }

    // ---------- 本层已认领 → 子目录只记一条父层依据，不再深入 ----------

    @Test
    void testParentFlavorPrunesChildSubtree() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/config.json");
        file(config, "alphamod/betamod/x.json");
        file(config, "alphamod/betamod/gammamod/notes.json");
        // 镜像：本层没有命中配置味文件，子目录照常往下判
        file(config, "deltamod/betamod/notes.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/betamod", "deltamod/betamod");
        assertVerdict(report, "alphamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.FLAVOR);
        assertDetail(report, "alphamod", "config.json");
        // 子目录的依据是父层命中的那个文件；孙目录不进报告，说明没有下钻
        assertVerdict(report, "alphamod/betamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_FLAVOR);
        assertDetail(report, "alphamod/betamod", "config.json");
        assertEquals(2, at(report, "alphamod/betamod").fileCount(), "被剪条目记自己整树的文件数");
        // 镜像：本层没有直接文件的目录不记账，只留下有文件的那层
        assertVerdict(report, "deltamod/betamod", SubdirReport.Verdict.UNRECOGNIZED, SubdirReport.Reason.NONE);
    }

    // ---------- 空目录 / 只含空目录的层不进报告 ----------

    @Test
    void testEmptyDirsAreNotReported() throws IOException {
        Path config = configRoot();
        dir(config, "alphamod/emptydir");
        dir(config, "betamod/onlydir/inner");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertEquals(List.of(), pathsOf(report));
    }

    // ---------- 纯路径层塌成最深的有文件那层 ----------

    @Test
    void testPathOnlyLayersCollapseToLayerWithFiles() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/betamod/gammamod/notes.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        only(report, "alphamod/betamod/gammamod");
        assertVerdict(report, "alphamod/betamod/gammamod", SubdirReport.Verdict.UNRECOGNIZED, SubdirReport.Reason.NONE);
    }

    // ---------- 被剪的空目录不记账 ----------

    @Test
    void testEmptyPrunedChildIsNotReported() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/config.json");
        dir(config, "alphamod/emptydir");
        file(config, "alphamod/betamod/x.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/betamod");
        assertVerdict(report, "alphamod/betamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_FLAVOR);
    }

    // ---------- 有文件但都不像 → UNRECOGNIZED（不是 SKIPPED） ----------

    @Test
    void testNoCandidateFileIsUnrecognized() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/notes.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        only(report, "alphamod");
        assertVerdict(report, "alphamod", SubdirReport.Verdict.UNRECOGNIZED, SubdirReport.Reason.NONE);
        assertNull(at(report, "alphamod").detail());
    }

    // ---------- 扁平剪枝只在第一层 ----------

    // 顶层不在扁平米里 → 正常下钻；第二层目录名与扁平文件同名也不该被剪
    @Test
    void testFlatPruningAppliesOnlyAtTopLevel() throws IOException {
        Path config = configRoot();
        file(config, "betamod.json");
        file(config, "alphamod/betamod/x.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of("betamod"));

        // 父层自己没有直接文件，不进报告；第二层同名不被剪，照常判定
        only(report, "alphamod/betamod");
        assertVerdict(report, "alphamod/betamod", SubdirReport.Verdict.UNRECOGNIZED, SubdirReport.Reason.NONE);
    }

    // 祖先被剪时，后代一起被挡在报告外
    @Test
    void testPrunedAncestorHidesItsSubtree() throws IOException {
        Path config = configRoot();
        file(config, "alphamod.json");
        file(config, "alphamod/betamod/x.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of("alphamod", "betamod"));

        only(report, "alphamod");
        assertVerdict(report, "alphamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.FLAT_FOUND);
    }

    // ---------- 段黑名单在未被剪枝的深层同样生效 ----------

    @Test
    void testNestedBlacklistedSegmentSkipped() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/betamod/world/x.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        only(report, "alphamod/betamod/world");
        assertVerdict(report, "alphamod/betamod/world", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.BLACKLIST);
        assertDetail(report, "alphamod/betamod/world", "world");
    }

    // ---------- 父层已认领时，深层黑名单段只按父层依据记一条 ----------

    @Test
    void testParentFlavorCoversNestedBlacklistedSegment() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/config.json");
        file(config, "alphamod/world/x.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/world");
        assertVerdict(report, "alphamod/world", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_FLAVOR);
        assertDetail(report, "alphamod/world", "config.json");
    }

    // ---------- 父层因子孙被认领 → ADOPTED/SUBDIR ----------

    @Test
    void testParentAdoptedBecauseOfDescendant() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/betamod/config.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/betamod");
        assertVerdict(report, "alphamod/betamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.FLAVOR);
        assertDetail(report, "alphamod/betamod", "config.json");
        // 本层没有东西，依据指向触发认领的那条路径
        assertVerdict(report, "alphamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.SUBDIR);
        assertDetail(report, "alphamod", "alphamod/betamod");
    }

    // ---------- 一层被归并 → 除触发者外，其他兄弟不管名字前后都改判成跳过 ----------

    @Test
    void testSiblingAdoptionRejudgesAllOtherSiblings() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/betamod/notes.json");
        file(config, "alphamod/gammamod/config.json");
        file(config, "alphamod/omegamod/x.json");
        file(config, "alphamod/omegamod/deep/notes.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/betamod", "alphamod/gammamod", "alphamod/omegamod");
        // 名字在触发者之前：原本的结论丢掉，换成归并依据
        assertVerdict(report, "alphamod/betamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR);
        assertDetail(report, "alphamod/betamod", "alphamod/gammamod");
        // 触发者保留自己的结论
        assertVerdict(report, "alphamod/gammamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.FLAVOR);
        assertDetail(report, "alphamod/gammamod", "config.json");
        // 名字在触发者之后：直接跳过，子树不进报告
        assertVerdict(report, "alphamod/omegamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR);
        assertDetail(report, "alphamod/omegamod", "alphamod/gammamod");
        assertVerdict(report, "alphamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.SUBDIR);
        assertDetail(report, "alphamod", "alphamod/gammamod");
    }

    // ---------- 已扫过的兄弟本身就是黑名单段：那条排除行不参与改判 ----------

    @Test
    void testBlacklistedSiblingKeepsItsOwnSkipRow() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/cache/x.json");
        file(config, "alphamod/gammamod/config.json");
        file(config, "alphamod/omegamod/x.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/cache", "alphamod/gammamod", "alphamod/omegamod");
        assertVerdict(report, "alphamod/cache", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.BLACKLIST);
        assertDetail(report, "alphamod/cache", "cache");
        assertVerdict(report, "alphamod/gammamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.FLAVOR);
        assertVerdict(report, "alphamod/omegamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR);
    }

    // ---------- 被扫过的支只改判不增删：纯路径层不留行，有文件的那层改成跳过 ----------

    @Test
    void testScannedSiblingKeepsShallowestRowWithSkipReason() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/betamod/deep/notes.json");
        file(config, "alphamod/gammamod/config.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/betamod/deep", "alphamod/gammamod");
        // 纯路径层 betamod 没有直接文件，本来就不占行
        assertVerdict(report, "alphamod/betamod/deep", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR);
        assertDetail(report, "alphamod/betamod/deep", "alphamod/gammamod");
        assertVerdict(report, "alphamod/gammamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.FLAVOR);
    }

    // ---------- 被归并的支里每一条活下来的行都改判 ----------

    @Test
    void testMergedBranchRejudgesEverySurvivingRow() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/betamod/notes.json");
        file(config, "alphamod/betamod/deep/x.json");
        file(config, "alphamod/gammamod/config.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/betamod", "alphamod/betamod/deep", "alphamod/gammamod");
        assertVerdict(report, "alphamod/betamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR);
        assertDetail(report, "alphamod/betamod", "alphamod/gammamod");
        assertVerdict(report, "alphamod/betamod/deep", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR);
        assertDetail(report, "alphamod/betamod/deep", "alphamod/gammamod");
        assertVerdict(report, "alphamod/gammamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.FLAVOR);
        assertDetail(report, "alphamod/gammamod", "config.json");
    }

    // ---------- 归并层里的空兄弟不留行 ----------

    @Test
    void testEmptySiblingLeavesNoRowInMergedLayer() throws IOException {
        Path config = configRoot();
        dir(config, "alphamod/emptydir");
        file(config, "alphamod/gammamod/config.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/gammamod");
        assertVerdict(report, "alphamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.SUBDIR);
        assertDetail(report, "alphamod", "alphamod/gammamod");
    }

    // ---------- 归并时深层黑名单行保留，同时给直接子目录补一条跳过 ----------

    @Test
    void testBlacklistedDeepRowSurvivesMerge() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/betamod/notes.json");
        file(config, "alphamod/betamod/cache/x.json");
        file(config, "alphamod/gammamod/config.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/betamod", "alphamod/betamod/cache", "alphamod/gammamod");
        assertVerdict(report, "alphamod/betamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR);
        assertDetail(report, "alphamod/betamod", "alphamod/gammamod");
        assertVerdict(report, "alphamod/betamod/cache", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.BLACKLIST);
        assertDetail(report, "alphamod/betamod/cache", "cache");
    }

    // ---------- 多个兄弟都能被认领时，取名字最靠前的那个当触发者 ----------

    @Test
    void testFirstAdoptedSiblingInNameOrderWins() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/betamod/config.json");
        file(config, "alphamod/gammamod/config.json");
        file(config, "alphamod/gammamod/deep/notes.json");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        assertPaths(report, "alphamod", "alphamod/betamod", "alphamod/gammamod");
        assertVerdict(report, "alphamod/betamod", SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.FLAVOR);
        // 后一个不再留下自己的结论，只留一条“被兄弟带过”的跳过
        assertVerdict(report, "alphamod/gammamod", SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR);
        assertDetail(report, "alphamod/gammamod", "alphamod/betamod");
        assertDetail(report, "alphamod", "alphamod/betamod");
    }

    // ---------- 只认白名单扩展名：client 是 FLAVOR 词，但 .png 不在扫描范围内 ----------

    @Test
    void testNonWhitelistedExtensionDoesNotAdopt() throws IOException {
        Path config = configRoot();
        file(config, "alphamod/client.png");

        SubdirReport report = SubdirAnalyzer.analyze(config, Set.of());

        only(report, "alphamod");
        assertVerdict(report, "alphamod", SubdirReport.Verdict.UNRECOGNIZED, SubdirReport.Reason.NONE);
        // 有直接文件但没有候选，仍然留在报告里
    }
}
