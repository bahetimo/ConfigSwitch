package com.bteamore.configswitch.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

import java.util.function.Consumer;

public abstract class ScrollableListWidget<E extends ContainerObjectSelectionList.Entry<E>> extends ContainerObjectSelectionList<E> {
    private Consumer<E> onRowClick = null;

    private static final int PANEL_FILL_MENU = 0x70000000;
    private static final int PANEL_FILL_WORLD = 0x50000000;
    private static final int PANEL_BORDER = 0x90000000;

    public ScrollableListWidget(Minecraft minecraftClient, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, y + height, itemHeight);

        this.setRenderBackground(false); // 1.20.1-only
        this.setRenderTopAndBottom(false); // 1.20.1-only

        // 条目从顶部开始排列，不随内容高度垂直居中
        this.centerListVertically = false;
    }

    @Override
    protected void renderBackground(GuiGraphics context) {
        int fill = (Minecraft.getInstance().level == null) ? PANEL_FILL_MENU : PANEL_FILL_WORLD;
        context.fill(this.x0, this.y0, this.x1, this.y1, fill);
        context.fill(this.x0, this.y0, this.x1, this.y0 + 1, PANEL_BORDER);
        context.fill(this.x0, this.y1 - 1, this.x1, this.y1, PANEL_BORDER);
        context.fill(this.x0, this.y0, this.x0 + 1, this.y1, PANEL_BORDER);
        context.fill(this.x1 - 1, this.y0, this.x1, this.y1, PANEL_BORDER);
    }

    public void setX(int x) {
        this.x0 = x;
        this.x1 = x + this.width;
    }

    public int getY() {
        return this.y0;
    }

    public int getHeight() {
        return this.height;
    }

    public void setOnRowClick(Consumer<E> onRowClick) {
        this.onRowClick = onRowClick;
    }

    @Override
    public int getRowWidth() {
        return this.width - (this.getMaxScroll() > 0 ? 38 : 32);
    }

    @Override
    protected int getScrollbarPosition() {
        return this.x1 - 6;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 每次左键都重算滚动条拖拽状态,行点击跳过它会把上次滚条点击的 scrolling 卡在 true
            this.updateScrollingState(mouseX, mouseY, button);
        }
        // 先卡控件边界，否则 getEntryAtPosition 不卡下边界，框外点击会命中隐藏行
        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            E entry = this.getEntryAtPosition(mouseX, mouseY);
            if (entry != null && this.onRowClick != null) {
                this.onRowClick.accept(entry);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
