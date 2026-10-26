package com.bteamore.configswitch.client.gui.widget.entries;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class BackupEntry extends ContainerObjectSelectionList.Entry<BackupEntry> {
    private static final int TEXT_INDENT = 8;
    private static final int TEXT_GAP = 12;
    private static final int RESTORE_BUTTON_WIDTH = 50;
    private static final int RESTORE_BUTTON_HEIGHT = 20;
    private static final int BUTTON_RIGHT_MARGIN = 8;
    private static final int TIMESTAMP_COLOR = 0xFFFFFF;
    private static final int FILE_COUNT_COLOR = 0xA0A0A0;

    private final String timestamp;
    private final int fileCount;
    private final Button restoreButton;

    public BackupEntry(String timestamp, int fileCount, Consumer<String> onRestore) {
        // 快照列表：时间戳 + 文件数 + 恢复按钮
        this.timestamp = timestamp;
        this.fileCount = fileCount;

        this.restoreButton = Button.builder(Component.translatable("configswitch.button.restore"), button -> onRestore.accept(timestamp))
                .bounds(0, 0, RESTORE_BUTTON_WIDTH, RESTORE_BUTTON_HEIGHT)
                .build();
    }

    @Override
    public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        Font font = Minecraft.getInstance().font;
        // 文本与按钮均在行内垂直居中，按钮右侧对齐
        int textY = y + (entryHeight - font.lineHeight) / 2;
        int buttonX = x + entryWidth - RESTORE_BUTTON_WIDTH - BUTTON_RIGHT_MARGIN;
        int buttonY = y + (entryHeight - RESTORE_BUTTON_HEIGHT) / 2;
        // 文本区右边界
        int maxTextWidth = buttonX - TEXT_GAP - (x + TEXT_INDENT);

        // 时间戳 亮色
        String trimmedTimestamp = trimWithEllipsis(font, this.timestamp, maxTextWidth);
        context.drawString(font, trimmedTimestamp, x + TEXT_INDENT, textY, TIMESTAMP_COLOR);
        // 文件数 暗色
        String fileCountText = Component.translatable("configswitch.label.file_count", this.fileCount).getString();
        int fileCountX = x + TEXT_INDENT + font.width(trimmedTimestamp) + TEXT_GAP;
        context.drawString(font, trimWithEllipsis(font, fileCountText, buttonX - TEXT_GAP - fileCountX), fileCountX, textY, FILE_COUNT_COLOR);

        // 恢复按钮
        this.restoreButton.setPosition(buttonX, buttonY);
        this.restoreButton.render(context, mouseX, mouseY, tickDelta);
    }

    private static String trimWithEllipsis(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, maxWidth - font.width(ellipsis)) + ellipsis;
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(this.restoreButton);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(this.restoreButton);
    }
}
