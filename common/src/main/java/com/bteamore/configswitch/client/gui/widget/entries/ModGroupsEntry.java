package com.bteamore.configswitch.client.gui.widget.entries;

import com.bteamore.configswitch.discovery.ModGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class ModGroupsEntry extends ContainerObjectSelectionList.Entry<ModGroupsEntry> {
    private static final int CHECKBOX_X = 6;
    private static final int CHECKBOX_Y = 4;
    private static final int CLASSIFY_BUTTON_WIDTH = 50;
    private static final int CLASSIFY_BUTTON_HEIGHT = 20;
    private static final int MOD_ID_INDENT = 34;
    private static final int FILE_INDENT = 44;
    private static final int MOD_ID_Y = 8;
    private static final int FILE_TOP = 24;
    private static final int LINE_HEIGHT = 11;
    private static final int MOD_ID_COLOR = 0xFFFFFF;
    private static final int MOD_ID_COLOR_UNCATEGORIZED = 0xA0A0A0;
    private static final int FILE_COLOR = 0xA0A0A0;
    private static final int TEXT_PADDING_RIGHT = 8;
    // 文件名区最多显示的行数（统一行高 46px 内只能放得下 2 行）
    private static final int FILE_LINES_MAX = 2;

    private final String modId;
    private final List<String> fileNames;
    private final Checkbox checkbox;
    private final int residueCount;
    private final Button classifyButton;

    public ModGroupsEntry(String modId, List<String> fileNames, boolean selected, int residueCount, BiConsumer<String, Boolean> onToggle, Runnable onClassify) {
        this.modId = modId;
        this.fileNames = fileNames;
        this.residueCount = residueCount;

        if (this.modId.equals(ModGroup.UNCATEGORIZED_ID)) {
            this.classifyButton = Button.builder(Component.translatable("configswitch.button.classify"), button -> onClassify.run())
                    .bounds(0, 0, CLASSIFY_BUTTON_WIDTH, CLASSIFY_BUTTON_HEIGHT)
                    .build();
        } else {
            this.classifyButton = null;
        }

        this.checkbox = Checkbox.builder(Component.empty(), Minecraft.getInstance().font)
                .pos(0, 0)
                .selected(selected)
                .onValueChange((checkbox, checked) -> onToggle.accept(modId, checked))
                .build();
    }

    @Override
    public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        Font font = Minecraft.getInstance().font;
        // 复选框：与 modId 行对齐
        this.checkbox.setPosition(x + CHECKBOX_X, y + CHECKBOX_Y);
        this.checkbox.render(context, mouseX, mouseY, tickDelta);
        // modId
        String text = switch (this.modId){
            case ModGroup.UNCATEGORIZED_ID -> {
                String base = Component.translatable("configswitch.group.uncategorized").getString();
                yield (this.residueCount > 0)
                        ? base + " - " + Component.translatable("configswitch.label.residue_hidden", this.residueCount).getString()
                        : base;
            }
            case ModGroup.VANILLA_ID -> Component.translatable("configswitch.group.vanilla").getString();
            default -> this.modId;
        };
        int modIdColor = Objects.equals(this.modId, ModGroup.UNCATEGORIZED_ID) ? MOD_ID_COLOR_UNCATEGORIZED : MOD_ID_COLOR;
        context.drawString(font,
                trimWithEllipsis(font, text, entryWidth - MOD_ID_INDENT - TEXT_PADDING_RIGHT),
                x + MOD_ID_INDENT,
                y + MOD_ID_Y,
                modIdColor);

        // 文件名最多显示 FILE_LINES_MAX 行（受统一行高限制），超出时改为"首个文件名 + 剩余数量提示"
        int fileY = y + FILE_TOP;
        int fileMaxWidth = entryWidth - FILE_INDENT - TEXT_PADDING_RIGHT;
        if (this.fileNames.size() <= FILE_LINES_MAX) {
            for (String fileName : this.fileNames) {
                context.drawString(font, trimWithEllipsis(font, fileName, fileMaxWidth), x + FILE_INDENT, fileY, FILE_COLOR);
                fileY += LINE_HEIGHT;
            }
        } else {
            context.drawString(font, trimWithEllipsis(font, this.fileNames.get(0), fileMaxWidth), x + FILE_INDENT, fileY, FILE_COLOR);
            context.drawString(font,
                    Component.translatable("configswitch.label.files_more", this.fileNames.size() - 1),
                    x + FILE_INDENT, fileY + LINE_HEIGHT, FILE_COLOR);
        }

        if (this.classifyButton != null) {
            this.classifyButton.setPosition(x + entryWidth - CLASSIFY_BUTTON_WIDTH - TEXT_PADDING_RIGHT, y + (entryHeight - CLASSIFY_BUTTON_HEIGHT) / 2);
            this.classifyButton.render(context, mouseX, mouseY, tickDelta);
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
        return this.classifyButton == null ? List.of(this.checkbox) : List.of(this.checkbox, this.classifyButton);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return this.classifyButton == null ? List.of(this.checkbox) : List.of(this.checkbox, this.classifyButton);
    }
}
