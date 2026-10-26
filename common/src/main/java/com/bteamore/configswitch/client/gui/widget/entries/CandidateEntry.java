package com.bteamore.configswitch.client.gui.widget.entries;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

import java.util.List;
import java.util.function.BooleanSupplier;

// 候选 mod 行：单击即归类（显示顺序由外部按相似度排好），行首标记当前归类目标。
// selected 为 Supplier，每帧现问，状态变化不需重建列表
public class CandidateEntry extends ContainerObjectSelectionList.Entry<CandidateEntry> {
    private static final String SELECT_MARK = "▸ ";
    private static final int TEXT_INDENT = 6;
    private static final int TEXT_PADDING_RIGHT = 8;
    private static final int MARK_COLOR = 0xFFFFFF;
    private static final int TEXT_COLOR = 0xA0A0A0;
    private static final int TEXT_COLOR_HOVERED = 0xFFFFFF;

    private final String modId;
    private final String displayName;
    private final BooleanSupplier selected;

    public CandidateEntry(String modId, String displayName, BooleanSupplier selected) {
        this.modId = modId;
        this.displayName = displayName;
        this.selected = selected;
    }

    public String getModId() {
        return this.modId;
    }

    @Override
    public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        Font font = Minecraft.getInstance().font;
        // 优先显示 displayName，缺失时退回 modId
        String text = (this.displayName == null || this.displayName.isBlank()) ? this.modId : this.displayName;
        int textY = y + (entryHeight - font.lineHeight) / 2;
        // 选中项行首加标记；未选中不画标记但保持同宽缩进，名称对齐
        if (this.selected.getAsBoolean()) {
            context.drawString(font, SELECT_MARK, x + TEXT_INDENT, textY, MARK_COLOR);
        }
        int nameX = x + TEXT_INDENT + font.width(SELECT_MARK);
        int maxWidth = x + entryWidth - TEXT_PADDING_RIGHT - nameX;
        int color = hovered ? TEXT_COLOR_HOVERED : TEXT_COLOR;
        context.drawString(font, trimWithEllipsis(font, text, maxWidth), nameX, textY, color);
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
