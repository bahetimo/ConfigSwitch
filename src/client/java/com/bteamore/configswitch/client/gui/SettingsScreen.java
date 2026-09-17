package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.config.ModConfig;
import com.bteamore.configswitch.config.ModSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class SettingsScreen extends Screen {
    private static final int TITLE_Y = 15;
    private static final int MARGIN_X = 20;
    private static final int FIELD_HEIGHT = 20;
    private static final int COUNT_CONTROL_WIDTH = 80;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    private static final int BOTTOM_MARGIN = 12;
    private static final int REPO_LABEL_Y = 55;
    private static final int REPO_FIELD_Y = 70;
    private static final int COUNT_LABEL_Y = 105;
    private static final int COUNT_CONTROL_Y = 100;
    private static final int RESET_BUTTON_WIDTH = 70;
    private static final int RESET_BUTTON_Y = 10;

    private TextFieldWidget repoField;
    private CyclingButtonWidget<Integer> countControl;
    private ButtonWidget backButton;
    private ButtonWidget saveButton;
    private ButtonWidget resetButton;
    private final Screen parentScreen;

    private ModSettings onDisk;

    private String editedRepoRoot;
    private int editedMaxBackupCount;
    private boolean messageIsError;
    private Text message;

    public SettingsScreen(Text title, Screen parent) {
        super(title);
        this.parentScreen = parent;
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
        // 仓库根路径：整行宽输入框
        int fieldWidth = this.width - MARGIN_X * 2;
        this.repoField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, MARGIN_X, REPO_FIELD_Y, fieldWidth, FIELD_HEIGHT, Text.translatable("configswitch.label.repo_root"));
        this.repoField.setMaxLength(512);
        this.repoField.setPlaceholder(Text.translatable("configswitch.label.repo_root"));
        this.repoField.setText(this.editedRepoRoot);

        // 保留备份数量：循环选择控件，固定宽右对齐
        this.countControl = CyclingButtonWidget.<Integer>builder(value -> Text.literal(String.valueOf(value)))
                .values(ModSettings.ALLOWED_BACKUP_COUNTS)
                .initially(this.editedMaxBackupCount)
                .omitKeyText()
                .build(this.width - MARGIN_X - COUNT_CONTROL_WIDTH, COUNT_CONTROL_Y, COUNT_CONTROL_WIDTH, FIELD_HEIGHT, Text.translatable("configswitch.label.max_backups"));

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

        this.addDrawableChild(this.repoField);
        this.addDrawableChild(this.countControl);
        this.addDrawableChild(this.backButton);
        this.addDrawableChild(this.saveButton);
        this.addDrawableChild(this.resetButton);
    }

    private void onReset() {
        ModSettings target = ModSettings.DEFAULT;
        if (ModConfig.store().save(target)) {
            this.onDisk = target;
            this.repoField.setText(target.repoRoot());
            this.countControl.setValue(target.maxBackupCount());
            this.messageIsError = false;
            this.message = Text.translatable("configswitch.message.reset_done");
        } else {
            this.messageIsError = true;
            this.message = Text.translatable("configswitch.message.reset_failed");
        }
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
        // 标签
        context.drawTextWithShadow(this.textRenderer, Text.translatable("configswitch.label.repo_root"), MARGIN_X, REPO_LABEL_Y, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("configswitch.label.max_backups"), MARGIN_X, COUNT_LABEL_Y, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parentScreen);
    }
}
