package com.bteamore.configswitch.client.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ElementListWidget;

public abstract class ScrollableListWidget<E extends ElementListWidget.Entry<E>> extends ElementListWidget<E> {
    public ScrollableListWidget(MinecraftClient minecraftClient, int i, int j, int k, int l) {
        super(minecraftClient, i, j, k, l);
        // 条目从顶部开始排列，不随内容高度垂直居中
        this.centerListVertically = false;
    }

    @Override
    public int getRowWidth() {
        return this.width - (this.isScrollbarVisible() ? 38 : 32);
    }

    @Override
    protected int getScrollbarX() {
        return this.getRight() - 6;
    }
}
