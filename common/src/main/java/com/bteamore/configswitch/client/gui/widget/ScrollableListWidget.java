package com.bteamore.configswitch.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

import java.util.function.Consumer;

public abstract class ScrollableListWidget<E extends ContainerObjectSelectionList.Entry<E>> extends ContainerObjectSelectionList<E> {
    private Consumer<E> onRowClick = null;

    public ScrollableListWidget(Minecraft minecraftClient, int i, int j, int k, int l) {
        super(minecraftClient, i, j, k, l);
        // 条目从顶部开始排列，不随内容高度垂直居中
        this.centerListVertically = false;
    }

    public void setOnRowClick(Consumer<E> onRowClick) {
        this.onRowClick = onRowClick;
    }

    @Override
    public int getRowWidth() {
        return this.width - (this.scrollbarVisible() ? 38 : 32);
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRight() - 6;
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
