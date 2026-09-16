package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.client.gui.widget.BackupListWidget;
import com.bteamore.configswitch.core.RestoreRequest;
import com.bteamore.configswitch.core.SyncOutcome;
import com.bteamore.configswitch.core.SyncReport;
import com.bteamore.configswitch.discovery.BackupScanner;
import com.bteamore.configswitch.discovery.BackupSnapshot;
import com.bteamore.configswitch.manager.StateManager;
import com.bteamore.configswitch.repo.RepoPaths;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

public class BackupScreen extends Screen {
    private enum Source {LOCAL, GLOBAL}

    private static final int TITLE_Y = 15;
    private static final int SOURCE_BUTTON_WIDTH = 70;
    private static final int SOURCE_BUTTON_HEIGHT = 20;
    private static final int SOURCE_BUTTON_GAP = 8;
    private static final int MARGIN_X = 20;
    private static final int LIST_TOP = 40;
    // 底部按钮行区域
    private static final int LIST_BOTTOM_GAP = 40;
    private static final int ITEM_HEIGHT = 24;
    private static final int BACK_BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 12;

    private final Path gameDir;
    private Source source = Source.LOCAL;

    private ButtonWidget globalSourceButton;
    private ButtonWidget localSourceButton;
    private BackupListWidget backupList;
    private ButtonWidget backButton;

    private String message;

    private final BackupScanner scanner = new BackupScanner();
    private final StateManager stateManager = StateManager.getInstance();

    private final Screen parentScreen;

    protected BackupScreen(Text title, Path gameDir, Screen parentScreen) {
        super(title);
        this.gameDir = gameDir;
        this.parentScreen = parentScreen;
    }

    private Path backupRoot() {
        return source == Source.LOCAL ? RepoPaths.localBackupDir(gameDir) : RepoPaths.backupDir();
    }

    private Path targetRoot() {
        return source == Source.LOCAL ? gameDir.resolve("config") : RepoPaths.commonDir();
    }

    @Override
    protected void init() {
        int listWidth = this.width - MARGIN_X * 2;
        int listHeight = this.height - LIST_TOP - LIST_BOTTOM_GAP;
        this.backupList = new BackupListWidget(MinecraftClient.getInstance(), listWidth, listHeight, LIST_TOP, ITEM_HEIGHT);
        this.backupList.setX(MARGIN_X);

        this.refreshList();

        // 底部：全局/本地切换居中并排，返回右下
        int buttonY = this.height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        int sourceRowWidth = SOURCE_BUTTON_WIDTH * 2 + SOURCE_BUTTON_GAP;
        int sourceStartX = (this.width - sourceRowWidth) / 2;
        // 来源切换：当前所在源的按钮置灰不可点，标明当前所在源
        this.globalSourceButton = ButtonWidget.builder(Text.literal("全局备份"), button -> {
                    switchSource(Source.GLOBAL);
                    this.refreshList();
                    updateSourceButton();
                })
                .dimensions(sourceStartX, buttonY, SOURCE_BUTTON_WIDTH, SOURCE_BUTTON_HEIGHT)
                .build();
        this.localSourceButton = ButtonWidget.builder(Text.literal("本地备份"), button -> {
                    switchSource(Source.LOCAL);
                    this.refreshList();
                    updateSourceButton();
                })
                .dimensions(sourceStartX + SOURCE_BUTTON_WIDTH + SOURCE_BUTTON_GAP, buttonY, SOURCE_BUTTON_WIDTH, SOURCE_BUTTON_HEIGHT)
                .build();
        updateSourceButton();

        // 返回
        this.backButton = ButtonWidget.builder(Text.literal("返回"), button -> this.close())
                .dimensions(this.width - MARGIN_X - BACK_BUTTON_WIDTH, buttonY, BACK_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        this.addDrawableChild(this.globalSourceButton);
        this.addDrawableChild(this.localSourceButton);
        this.addDrawableChild(this.backupList);
        this.addDrawableChild(this.backButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        // 暂时显示状态机的当前状态或 msg
        String msg = (this.message == null) ? ("当前状态：" + stateManager.getCurrentState().name()) : this.message;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(msg), this.width / 2, TITLE_Y + 12, 0xAAAAAA);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parentScreen);
    }

    private void refreshList() {
        List<BackupSnapshot> snapshots = scanner.scan(backupRoot());
        this.backupList.clearBackups();
        for (BackupSnapshot snapshot : snapshots) {
            this.backupList.addBackup(snapshot.timeStamp(), snapshot.relativePaths().size(), ts -> this.confirmRestore(snapshot));
        }
    }

    private void updateSourceButton() {
        this.globalSourceButton.active = this.source != Source.GLOBAL;
        this.localSourceButton.active = this.source != Source.LOCAL;
    }

    // 二次确认
    private void confirmRestore(BackupSnapshot snapshot) {
        MinecraftClient client = MinecraftClient.getInstance();
        String sourceName = this.source == Source.LOCAL ? "本地配置" : "全局配置";
        client.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        this.onRestore(snapshot);
                    }
                    // 不自动返回上级，需手动返回
                    client.setScreen(this);
                },
                Text.literal("确认恢复"),
                Text.literal("将使用快照 " + snapshot.timeStamp() + " 覆盖当前 " + snapshot.relativePaths().size()
                        + " 个文件（" + sourceName + "）。\n恢复前会自动备份当前文件，确定继续吗？"),
                Text.literal("恢复"),
                Text.literal("取消")));
    }

    private void onRestore(BackupSnapshot snapshot) {
        RestoreRequest request = new RestoreRequest(snapshot, targetRoot(), backupRoot(), report -> this.message = buildMessage(report));
        this.stateManager.transition("restore", request);
        if (this.source == Source.LOCAL) {
            this.client.options.load();
        }
        this.refreshList();
    }

    private String buildMessage(SyncReport report) {
        int success = report.count(SyncOutcome.SUCCESS);
        int failed = report.count(SyncOutcome.FAILED);
        int skipped = report.count(SyncOutcome.SKIPPED);

        if (report.results().isEmpty()) {
            return "没有可恢复的内容";
        }
        if (success == 0 && failed == 0) {
            return "没有可恢复的内容，" + skipped + " 个跳过";
        }

        String join = String.join("、", report.failedModIds());

        return String.format("恢复完成：%d 个文件", success) +
                (failed > 0 ? String.format("，%d 个失败（%s）", failed, join) : "") +
                (skipped > 0 ? String.format("，%d 个跳过", skipped) : "") +
                ((source.equals(Source.LOCAL) && involvesModConfig(report)) ? "；部分配置需重启游戏生效" : "");
    }

    private boolean involvesModConfig(SyncReport report) {
        return report.results().keySet().stream()
                .anyMatch(key -> !"options.txt".equals(key));
    }

    private void switchSource(Source source) {
        this.source = source;
    }
}
