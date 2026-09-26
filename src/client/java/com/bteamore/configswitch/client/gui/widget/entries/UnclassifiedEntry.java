package com.bteamore.configswitch.client.gui.widget.entries;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

// 未分类文件行：单击选中，行首标记区分选中项；已归类文件在文件名后显示 " -> modid" 标注（suffix）。
// suffix / selected 均为 Supplier，每帧现问，状态变化不需重建列表
public class UnclassifiedEntry extends ElementListWidget.Entry<UnclassifiedEntry> {
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
    public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int textY = y + (entryHeight - textRenderer.fontHeight) / 2;
        boolean selected = this.selected.getAsBoolean();
        // 选中项行首加标记；未选中不画标记但保持同宽缩进，文件名对齐
        if (selected) {
            context.drawTextWithShadow(textRenderer, SELECT_MARK, x + TEXT_INDENT, textY, MARK_COLOR);
        }
        int nameX = x + TEXT_INDENT + textRenderer.getWidth(SELECT_MARK);
        int maxWidth = x + entryWidth - TEXT_PADDING_RIGHT - nameX;
        int color = selected ? TEXT_COLOR_SELECTED : (hovered ? TEXT_COLOR_HOVERED : TEXT_COLOR);
        // 归类标注优先保留：文件名按剩余宽度裁剪，标注始终可见
        String suffixText = this.suffix.get();
        int suffixWidth = textRenderer.getWidth(suffixText);
        String name = trimWithEllipsis(textRenderer, this.fileName, Math.max(0, maxWidth - suffixWidth));
        context.drawTextWithShadow(textRenderer, name, nameX, textY, color);
        if (!suffixText.isEmpty()) {
            context.drawTextWithShadow(textRenderer, suffixText, nameX + textRenderer.getWidth(name), textY, color);
        }
    }

    private static String trimWithEllipsis(TextRenderer textRenderer, String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        return textRenderer.trimToWidth(text, maxWidth - textRenderer.getWidth(ellipsis)) + ellipsis;
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
        return List.of();
    }

    @Override
    public List<? extends Element> children() {
        return List.of();
    }
}
