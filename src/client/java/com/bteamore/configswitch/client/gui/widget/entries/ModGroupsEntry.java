package com.bteamore.configswitch.client.gui.widget.entries;

import com.bteamore.configswitch.discovery.ModGroup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class ModGroupsEntry extends ElementListWidget.Entry<ModGroupsEntry> {
    private static final int CHECKBOX_X = 6;
    private static final int CHECKBOX_Y = 4;
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
    private final CheckboxWidget checkbox;

    public ModGroupsEntry(String modId, List<String> fileNames, boolean selected, BiConsumer<String, Boolean> onToggle) {
        this.modId = modId;
        this.fileNames = fileNames;

        this.checkbox = CheckboxWidget.builder(Text.empty(), MinecraftClient.getInstance().textRenderer)
                .pos(0, 0)
                .checked(selected)
                .callback((checkbox, checked) -> onToggle.accept(modId, checked))
                .build();
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        // 复选框：与 modId 行对齐
        this.checkbox.setPosition(x + CHECKBOX_X, y + CHECKBOX_Y);
        this.checkbox.render(context, mouseX, mouseY, tickDelta);
        // modId
        String text = switch (this.modId){
            case ModGroup.UNCATEGORIZED_ID -> Text.translatable("configswitch.group.uncategorized").getString();
            case ModGroup.VANILLA_ID -> Text.translatable("configswitch.group.vanilla").getString();
            default -> this.modId;
        };
        int modIdColor = Objects.equals(this.modId, ModGroup.UNCATEGORIZED_ID) ? MOD_ID_COLOR_UNCATEGORIZED : MOD_ID_COLOR;
        context.drawTextWithShadow(textRenderer,
                trimWithEllipsis(textRenderer, text, entryWidth - MOD_ID_INDENT - TEXT_PADDING_RIGHT),
                x + MOD_ID_INDENT,
                y + MOD_ID_Y,
                modIdColor);

        // 文件名最多显示 FILE_LINES_MAX 行（受统一行高限制），超出时改为"首个文件名 + 剩余数量提示"
        int fileY = y + FILE_TOP;
        int fileMaxWidth = entryWidth - FILE_INDENT - TEXT_PADDING_RIGHT;
        if (this.fileNames.size() <= FILE_LINES_MAX) {
            for (String fileName : this.fileNames) {
                context.drawTextWithShadow(textRenderer, trimWithEllipsis(textRenderer, fileName, fileMaxWidth), x + FILE_INDENT, fileY, FILE_COLOR);
                fileY += LINE_HEIGHT;
            }
        } else {
            context.drawTextWithShadow(textRenderer, trimWithEllipsis(textRenderer, this.fileNames.get(0), fileMaxWidth), x + FILE_INDENT, fileY, FILE_COLOR);
            context.drawTextWithShadow(textRenderer,
                    Text.translatable("configswitch.label.files_more", this.fileNames.size() - 1),
                    x + FILE_INDENT, fileY + LINE_HEIGHT, FILE_COLOR);
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
        return List.of(this.checkbox);
    }

    @Override
    public List<? extends Element> children() {
        return List.of(this.checkbox);
    }
}
