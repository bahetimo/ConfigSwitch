package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.client.gui.widget.TransferPanel;
import com.bteamore.configswitch.config.ModConfig;
import com.bteamore.configswitch.config.ModSettings;
import com.bteamore.configswitch.core.ExportRequest;
import com.bteamore.configswitch.core.ImportRequest;
import com.bteamore.configswitch.core.PackReport;
import com.bteamore.configswitch.discovery.MappingConfig;
import com.bteamore.configswitch.manager.StateManager;
import com.bteamore.configswitch.repo.RepoPaths;
import com.bteamore.configswitch.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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

    private TextFieldWidget repoField;
    private CyclingButtonWidget<Integer> countControl;
    private ButtonWidget backButton;
    private ButtonWidget saveButton;
    private ButtonWidget resetButton;
    private ButtonWidget exportButton;
    private ButtonWidget importButton;
    private TransferPanel transferPanel;
    private final Screen parentScreen;

    private final StateManager stateManager;

    private ModSettings onDisk;

    private String editedRepoRoot;
    private int editedMaxBackupCount;
    private boolean messageIsError;
    private Text message;
    private boolean panelVisible;
    private lastResult lastResult;
    private Runnable onRepoChanged;

    private record lastResult(TransferPanel.Kind kind, PackReport report, Path openTarget) {}

    public SettingsScreen(Text title, Screen parent, StateManager stateManager, Runnable onRepoChanged) {
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
            this.editedRepoRoot = this.repoField.getText();
            this.editedMaxBackupCount = this.countControl.getValue();
        }
        // 仓库根路径：标签右侧的整行宽输入框
        int fieldWidth = this.width - MARGIN_X - CONTROL_X;
        this.repoField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, CONTROL_X, REPO_FIELD_Y, fieldWidth, FIELD_HEIGHT, Text.translatable("configswitch.label.repo_root"));
        this.repoField.setMaxLength(512);
        this.repoField.setPlaceholder(Text.translatable("configswitch.label.repo_root"));
        this.repoField.setText(this.editedRepoRoot);

        // 保留备份数量：循环选择控件，位于标签列之后
        this.countControl = CyclingButtonWidget.<Integer>builder(value -> Text.literal(String.valueOf(value)))
                .values(ModSettings.ALLOWED_BACKUP_COUNTS)
                .initially(this.editedMaxBackupCount)
                .omitKeyText()
                .build(CONTROL_X, COUNT_CONTROL_Y, COUNT_CONTROL_WIDTH, FIELD_HEIGHT, Text.translatable("configswitch.label.max_backups"));

        // 底部：返回 / 保存居中并排
        int buttonY = this.height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        int rowWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int startX = (this.width - rowWidth) / 2;
        this.backButton = ButtonWidget.builder(Text.translatable("configswitch.button.back"), button -> this.onBack())
                .dimensions(startX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.saveButton = ButtonWidget.builder(Text.translatable("configswitch.button.save"), button -> this.onSave())
                .dimensions(startX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        // 右上角：重置配置
        this.resetButton = ButtonWidget.builder(Text.translatable("configswitch.button.reset"), button ->  this.client.setScreen(new ConfirmScreen(
                        result -> {
                            if (result) {
                                this.onReset();
                            }
                            this.client.setScreen(this);
                        },
                        Text.translatable("configswitch.confirm.reset.title"),
                        Text.translatable("configswitch.confirm.reset.text"),
                        Text.translatable("configswitch.confirm.reset.yes"),
                        Text.translatable("configswitch.confirm.discard.no"))))
                .dimensions(this.width - MARGIN_X - RESET_BUTTON_WIDTH, RESET_BUTTON_Y, RESET_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        // 分隔线下方：导出 / 导入仓库
        this.exportButton = ButtonWidget.builder(Text.translatable("configswitch.button.export"), btn -> {
                    onExport();
                })
                .dimensions(CONTROL_X, TRANSFER_ROW_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        this.importButton = ButtonWidget.builder(Text.translatable("configswitch.button.import"), btn -> enterImportMode())
                .dimensions(CONTROL_X + BUTTON_WIDTH + BUTTON_GAP, TRANSFER_ROW_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        // 遮罩屏
        this.transferPanel = new TransferPanel(this.width, this.height, this::exitPanelMode);

        this.addDrawableChild(this.repoField);
        this.addDrawableChild(this.countControl);
        this.addDrawableChild(this.backButton);
        this.addDrawableChild(this.saveButton);
        this.addDrawableChild(this.resetButton);
        this.addDrawableChild(this.exportButton);
        this.addDrawableChild(this.importButton);
        this.addDrawableChild(this.transferPanel);

        // 控件都是新建的，遮罩态必须按 importMode 重放
        this.restorePanel();
    }

    @Override
    public void filesDragged(List<Path> paths) {
        if (!this.transferPanel.isWaitingDrop()) {
            return;
        }
        Path zip = this.transferPanel.acceptDrop(paths);

        if (zip == null) {
            return;
        }

        ImportRequest request = new ImportRequest(RepoPaths.root(), zip, RepoPaths.importBackupDir(), report -> this.onComplete("import", report));

        this.stateManager.transition("import", request);
    }

    private void onExport() {
        this.message = null;
        String version = FabricLoader.getInstance().getModContainer(Log.NAME)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        ExportRequest request = new ExportRequest(RepoPaths.root(), RepoPaths.exportDir(), version, (PackReport report) -> this.onComplete("export", report));

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
        this.repoField.setText(target.repoRoot());
        this.countControl.setValue(target.maxBackupCount());
        this.messageIsError = false;
        this.message = Text.translatable("configswitch.message.reset_done");
    }

    private void onBack() {
        String repoRoot = this.repoField.getText();
        int maxBackupCount = this.countControl.getValue();
        if (!repoRoot.equals(this.onDisk.repoRoot()) || maxBackupCount != this.onDisk.maxBackupCount()) {
            this.client.setScreen(new ConfirmScreen(
                    result -> {
                        if (result) {
                            this.close();
                        } else {
                            this.client.setScreen(this);
                        }
                    },
                    Text.translatable("configswitch.confirm.discard.title"),
                    Text.translatable("configswitch.confirm.discard.text"),
                    Text.translatable("configswitch.confirm.discard.yes"),
                    Text.translatable("configswitch.confirm.discard.no")));
        } else {
            this.close();
        }
    }

    private void onSave() {
        String repoRoot = this.repoField.getText();
        String repoRootValidation = ModSettings.validateRepoRoot(repoRoot);
        if (repoRootValidation != null) {
            this.messageIsError = true;
            // validateRepoRoot 在 main 层，返回的是翻译 key，这里翻译成文案
            this.message = Text.translatable(repoRootValidation);
            return;
        }
        int maxBackupCount = this.countControl.getValue();
        ModSettings updated = new ModSettings(repoRoot, maxBackupCount);
        if (ModConfig.store().save(updated)) {
            this.onDisk = updated;
            this.messageIsError = false;
            this.message = Text.translatable("configswitch.message.saved");
        } else {
            this.messageIsError = true;
            this.message = Text.translatable("configswitch.message.save_failed");
        }

    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        if (this.message != null) {
            int color = this.messageIsError ? 0xFF5555 : 0xAAAAAA;
            context.drawCenteredTextWithShadow(this.textRenderer, this.message, this.width / 2, TITLE_Y + 12, color);
        }

        if (this.panelVisible) {
            return;
        }
        // 标签（与各控件行垂直对齐）
        context.drawTextWithShadow(this.textRenderer, Text.translatable("configswitch.label.repo_root"), MARGIN_X, REPO_FIELD_Y + LABEL_DY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("configswitch.label.max_backups"), MARGIN_X, COUNT_CONTROL_Y + LABEL_DY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("configswitch.label.repo_export"), MARGIN_X, TRANSFER_ROW_Y + LABEL_DY, 0xFFFFFF);
        // 设置区 / 仓库操作区 分隔线
        context.fill(MARGIN_X, DIVIDER_Y, this.width - MARGIN_X, DIVIDER_Y + 1, 0xFF808080);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.panelVisible && super.shouldCloseOnEsc();
    }

    @Override
    public void close() {
        this.client.setScreen(this.parentScreen);
    }
}
