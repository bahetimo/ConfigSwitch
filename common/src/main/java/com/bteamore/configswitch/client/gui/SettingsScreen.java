package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.client.gui.widget.TransferPanel;
import com.bteamore.configswitch.config.ModConfig;
import com.bteamore.configswitch.config.ModSettings;
import com.bteamore.configswitch.core.ExportRequest;
import com.bteamore.configswitch.core.ImportRequest;
import com.bteamore.configswitch.core.PackReport;
import com.bteamore.configswitch.discovery.MappingConfig;
import com.bteamore.configswitch.manager.StateManager;
import com.bteamore.configswitch.platform.Platforms;
import com.bteamore.configswitch.repo.RepoPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

public class SettingsScreen extends Screen {
    private static final int TITLE_Y = 15;
    private static final int MARGIN_X = 20;
    private static final int FIELD_HEIGHT = 20;
    private static final int COUNT_CONTROL_WIDTH = 80;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    private static final int BOTTOM_MARGIN = 12;
    private static final int LABEL_COLUMN_WIDTH = 100;
    private static final int CONTROL_X = MARGIN_X + LABEL_COLUMN_WIDTH;
    private static final int REPO_FIELD_Y = 50;
    private static final int COUNT_CONTROL_Y = 80;
    private static final int DIVIDER_Y = 110;
    private static final int TRANSFER_ROW_Y = 125;
    private static final int LABEL_DY = 5;
    private static final int RESET_BUTTON_WIDTH = 70;
    private static final int RESET_BUTTON_Y = 10;

    private EditBox repoField;
    private CycleButton<Integer> countControl;
    private Button backButton;
    private Button saveButton;
    private Button resetButton;
    private Button exportButton;
    private Button importButton;
    private TransferPanel transferPanel;
    private final Screen parentScreen;

    private final StateManager stateManager;

    private ModSettings onDisk;

    private String editedRepoRoot;
    private int editedMaxBackupCount;
    private boolean messageIsError;
    private Component message;
    private boolean panelVisible;
    private lastResult lastResult;
    private Runnable onRepoChanged;

    private record lastResult(TransferPanel.Kind kind, PackReport report, Path openTarget) {}

    public SettingsScreen(Component title, Screen parent, StateManager stateManager, Runnable onRepoChanged) {
        super(title);
        this.parentScreen = parent;
        this.stateManager = stateManager;
        this.onRepoChanged = onRepoChanged;
    }

    @Override
    protected void init() {
        if (this.onDisk == null) {
            this.onDisk = ModConfig.store().load();
            this.editedRepoRoot = this.onDisk.repoRoot();
            this.editedMaxBackupCount = (ModSettings.ALLOWED_BACKUP_COUNTS.contains(this.onDisk.maxBackupCount()) ? this.onDisk.maxBackupCount() : ModSettings.DEFAULT_MAX_BACKUP_COUNT) ;
        } else {
            this.editedRepoRoot = this.repoField.getValue();
            this.editedMaxBackupCount = this.countControl.getValue();
        }
        // 仓库根路径：标签右侧的整行宽输入框
        int fieldWidth = this.width - MARGIN_X - CONTROL_X;
        this.repoField = new EditBox(Minecraft.getInstance().font, CONTROL_X, REPO_FIELD_Y, fieldWidth, FIELD_HEIGHT, Component.translatable("configswitch.label.repo_root"));
        this.repoField.setMaxLength(512);
        this.repoField.setHint(Component.translatable("configswitch.label.repo_root"));
        this.repoField.setValue(this.editedRepoRoot);

        // 保留备份数量：循环选择控件，位于标签列之后
        this.countControl = CycleButton.<Integer>builder(value -> Component.literal(String.valueOf(value)))
                .withValues(ModSettings.ALLOWED_BACKUP_COUNTS)
                .withInitialValue(this.editedMaxBackupCount)
                .displayOnlyValue()
                .create(CONTROL_X, COUNT_CONTROL_Y, COUNT_CONTROL_WIDTH, FIELD_HEIGHT, Component.translatable("configswitch.label.max_backups"));

        // 底部：返回 / 保存居中并排
        int buttonY = this.height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        int rowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int startX = (this.width - rowWidth) / 2;
        this.backButton = Button.builder(Component.translatable("configswitch.button.back"), button -> this.onBack())
                .bounds(startX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.saveButton = Button.builder(Component.translatable("configswitch.button.save"), button -> this.onSave())
                .bounds(startX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        // 右上角：重置配置
        this.resetButton = Button.builder(Component.translatable("configswitch.button.reset"), button ->  this.minecraft.setScreen(new ConfirmScreen(
                        result -> {
                            if (result) {
                                this.onReset();
                            }
                            this.minecraft.setScreen(this);
                        },
                        Component.translatable("configswitch.confirm.reset.title"),
                        Component.translatable("configswitch.confirm.reset.text"),
                        Component.translatable("configswitch.confirm.reset.yes"),
                        Component.translatable("configswitch.confirm.discard.no"))))
                .bounds(this.width - MARGIN_X - RESET_BUTTON_WIDTH, RESET_BUTTON_Y, RESET_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        // 分隔线下方：导出 / 导入仓库
        this.exportButton = Button.builder(Component.translatable("configswitch.button.export"), btn -> {
                    onExport();
                })
                .bounds(CONTROL_X, TRANSFER_ROW_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.importButton = Button.builder(Component.translatable("configswitch.button.import"), btn -> enterImportMode())
                .bounds(CONTROL_X + BUTTON_WIDTH + BUTTON_GAP, TRANSFER_ROW_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        // 遮罩屏
        this.transferPanel = new TransferPanel(this.width, this.height, this::exitPanelMode);

        this.addRenderableWidget(this.repoField);
        this.addRenderableWidget(this.countControl);
        this.addRenderableWidget(this.backButton);
        this.addRenderableWidget(this.saveButton);
        this.addRenderableWidget(this.resetButton);
        this.addRenderableWidget(this.exportButton);
        this.addRenderableWidget(this.importButton);
        this.addRenderableWidget(this.transferPanel);

        // 控件都是新建的，遮罩态必须按 importMode 重放
        this.restorePanel();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!this.transferPanel.isWaitingDrop()) {
            return;
        }
        Path zip = this.transferPanel.acceptDrop(paths);

        if (zip == null) {
            return;
        }

        ImportRequest request = new ImportRequest(RepoPaths.repoRoot(), RepoPaths.mappingsFile(), zip, RepoPaths.importBackupDir(), report -> this.onComplete("import", report));

        this.stateManager.transition("import", request);
    }

    private void onExport() {
        this.message = null;
        String version = Platforms.get().selfVersion();

        ExportRequest request = new ExportRequest(RepoPaths.repoRoot(), RepoPaths.mappingsFile(), RepoPaths.exportDir(), version, (PackReport report) -> this.onComplete("export", report));

        this.stateManager.transition("export", request);
    }

    private void enterImportMode() {
        this.panelVisible = true;
        this.lastResult = null;
        this.message = null;
        this.setControlsVisible(false);
        this.transferPanel.beginDrop();
    }

    private void exitPanelMode() {
        this.panelVisible = false;
        this.lastResult = null;
        this.setControlsVisible(true);
    }

    // 成组控件的显隐只在这里定义：屏的 init() 会被窗口缩放重跑，
    // 两边各写一份可见性必然打架
    private void setControlsVisible(boolean visible) {
        this.repoField.setVisible(visible);
        this.countControl.visible = visible;
        this.backButton.visible = visible;
        this.saveButton.visible = visible;
        this.resetButton.visible = visible;
        this.exportButton.visible = visible;
        this.importButton.visible = visible;
    }

    private void restorePanel() {
        if (!this.panelVisible) {
            return;
        }
        this.setControlsVisible(false);
        // 面板也是新对象，上一次的结果态随旧面板丢了，这里退回等待拖入
        if (this.lastResult == null) {
            this.transferPanel.beginDrop();
        } else {
            this.transferPanel.showResult(this.lastResult.kind, this.lastResult.report, this.lastResult.openTarget);
        }
    }

    private void onComplete(String type, PackReport report) {
        Path openTarget = null;
        TransferPanel.Kind kind = TransferPanel.Kind.valueOf(type.toUpperCase());
        switch (kind) {
            case IMPORT -> {
                openTarget = RepoPaths.importBackupDir();
                MappingConfig.reload();
                this.onRepoChanged.run();
            }
            case EXPORT -> {
                openTarget = report.location();
                if (openTarget != null) {
                    openTarget = openTarget.getParent();
                }
                this.panelVisible = true;
                this.setControlsVisible(false);
            }
        }
        this.lastResult = new lastResult(kind, report, openTarget);

        this.transferPanel.showResult(kind, report, openTarget);
    }

    private void onReset() {
        ModSettings target = ModSettings.DEFAULT;
        this.repoField.setValue(target.repoRoot());
        this.countControl.setValue(target.maxBackupCount());
        this.messageIsError = false;
        this.message = Component.translatable("configswitch.message.reset_done");
    }

    private void onBack() {
        String repoRoot = this.repoField.getValue();
        int maxBackupCount = this.countControl.getValue();
        if (!repoRoot.equals(this.onDisk.repoRoot()) || maxBackupCount != this.onDisk.maxBackupCount()) {
            this.minecraft.setScreen(new ConfirmScreen(
                    result -> {
                        if (result) {
                            this.onClose();
                        } else {
                            this.minecraft.setScreen(this);
                        }
                    },
                    Component.translatable("configswitch.confirm.discard.title"),
                    Component.translatable("configswitch.confirm.discard.text"),
                    Component.translatable("configswitch.confirm.discard.yes"),
                    Component.translatable("configswitch.confirm.discard.no")));
        } else {
            this.onClose();
        }
    }

    private void onSave() {
        String repoRoot = this.repoField.getValue();
        String repoRootValidation = ModSettings.validateRepoRoot(repoRoot);
        if (repoRootValidation != null) {
            this.messageIsError = true;
            // validateRepoRoot 在 main 层，返回的是翻译 key，这里翻译成文案
            this.message = Component.translatable(repoRootValidation);
            return;
        }
        int maxBackupCount = this.countControl.getValue();
        ModSettings updated = new ModSettings(repoRoot, maxBackupCount);
        if (ModConfig.store().save(updated)) {
            this.onDisk = updated;
            this.messageIsError = false;
            this.message = Component.translatable("configswitch.message.saved");
        } else {
            this.messageIsError = true;
            this.message = Component.translatable("configswitch.message.save_failed");
        }

    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // 标题
        context.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        if (this.message != null) {
            int color = this.messageIsError ? 0xFF5555 : 0xAAAAAA;
            context.drawCenteredString(this.font, this.message, this.width / 2, TITLE_Y + 12, color);
        }

        if (this.panelVisible) {
            return;
        }
        // 标签（与各控件行垂直对齐）
        context.drawString(this.font, Component.translatable("configswitch.label.repo_root"), MARGIN_X, REPO_FIELD_Y + LABEL_DY, 0xFFFFFF);
        context.drawString(this.font, Component.translatable("configswitch.label.max_backups"), MARGIN_X, COUNT_CONTROL_Y + LABEL_DY, 0xFFFFFF);
        context.drawString(this.font, Component.translatable("configswitch.label.repo_export"), MARGIN_X, TRANSFER_ROW_Y + LABEL_DY, 0xFFFFFF);
        // 设置区 / 仓库操作区 分隔线
        context.fill(MARGIN_X, DIVIDER_Y, this.width - MARGIN_X, DIVIDER_Y + 1, 0xFF808080);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.panelVisible && super.shouldCloseOnEsc();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
}
