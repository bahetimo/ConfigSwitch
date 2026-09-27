package com.bteamore.configswitch.client.gui.widget;

import com.bteamore.configswitch.client.gui.widget.entries.UnclassifiedEntry;
import net.minecraft.client.MinecraftClient;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

// 左栏：未分类文件列表；整行可点，点击回调文件名
public class UnclassifiedListWidget extends ScrollableListWidget<UnclassifiedEntry> {
    public UnclassifiedListWidget(MinecraftClient minecraftClient, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
    }

    public void setOnSelect(Consumer<String> onSelect) {
        this.setOnRowClick(entry -> onSelect.accept(entry.getFileName()));
    }

    public void addFile(String fileName, Supplier<String> suffix, BooleanSupplier selected) {
        this.addEntry(new UnclassifiedEntry(fileName, suffix, selected));
    }

    public void clearFiles() {
        this.clearEntries();
    }
}
