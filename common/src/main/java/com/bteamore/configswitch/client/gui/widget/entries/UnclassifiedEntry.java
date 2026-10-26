package com.bteamore.configswitch.client.gui.widget.entries;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

// 未分类文件行：单击选中，行首标记区分选中项；已归类文件在文件名后显示 " -> modid" 标注（suffix）。
// suffix / selected 均为 Supplier，每帧现问，状态变化不需重建列表
public class UnclassifiedEntry extends ContainerObjectSelectionList.Entry<UnclassifiedEntry> {
    private static final String SELECT_MARK = "▸ ";
    private static final int TEXT_INDENT = 4;
    private static final int TEXT_PADDING_RIGHT = 8;
    private static final int MARK_COLOR = 0xFFFFFF;
    private static final int TEXT_COLOR = 0xA0A0A0;
    private static final int TEXT_COLOR_SELECTED = 0xFFFFFF;
    private static final int TEXT_COLOR_HOVERED = 0xD0D0D0;

    private final String fileName;
    private final Supplier<String> suffix;
    private final BooleanSupplier selected;

    public UnclassifiedEntry(String fileName, Supplier<String> suffix, BooleanSupplier selected) {
        this.fileName = fileName;
        this.suffix = suffix;
        this.selected = selected;
    }

    public String getFileName() {
        return this.fileName;
    }

    @Override
    public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        Font font = Minecraft.getInstance().font;
        int textY = y + (entryHeight - font.lineHeight) / 2;
        boolean selected = this.selected.getAsBoolean();
        // 选中项行首加标记；未选中不画标记但保持同宽缩进，文件名对齐
        if (selected) {
            context.drawString(font, SELECT_MARK, x + TEXT_INDENT, textY, MARK_COLOR);
        }
        int nameX = x + TEXT_INDENT + font.width(SELECT_MARK);
        int maxWidth = x + entryWidth - TEXT_PADDING_RIGHT - nameX;
        int color = selected ? TEXT_COLOR_SELECTED : (hovered ? TEXT_COLOR_HOVERED : TEXT_COLOR);
        // 归类标注优先保留：文件名按剩余宽度裁剪，标注始终可见
        String suffixText = this.suffix.get();
        int suffixWidth = font.width(suffixText);
        String name = trimWithEllipsis(font, this.fileName, Math.max(0, maxWidth - suffixWidth));
        context.drawString(font, name, nameX, textY, color);
        if (!suffixText.isEmpty()) {
            context.drawString(font, suffixText, nameX + font.width(name), textY, color);
        }
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
        return List.of();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of();
    }
}
