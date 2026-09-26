package com.bteamore.configswitch.client.gui.widget;

import com.bteamore.configswitch.client.gui.widget.entries.UnclassifiedEntry;
import net.minecraft.client.MinecraftClient;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

// 左栏：未分类文件列表；整行可点，点击回调文件名
public class UnclassifiedListWidget extends ScrollableListWidget<UnclassifiedEntry> {
    private Consumer<String> onSelect = fileName -> {
    };

    public UnclassifiedListWidget(MinecraftClient minecraftClient, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
    }

    public void setOnSelect(Consumer<String> onSelect) {
        this.onSelect = onSelect;
    }

    public void addFile(String fileName, Supplier<String> suffix, BooleanSupplier selected) {
        this.addEntry(new UnclassifiedEntry(fileName, suffix, selected));
    }

    public void clearFiles() {
        this.clearEntries();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 每次左键都重算滚动条拖拽状态,行点击跳过它会把上次滚条点击的 scrolling 卡在 true
            this.updateScrollingState(mouseX, mouseY, button);
        }
        // 先卡控件边界，否则 getEntryAtPosition 不卡下边界，框外点击会命中隐藏行
        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            UnclassifiedEntry entry = this.getEntryAtPosition(mouseX, mouseY);
            if (entry != null) {
                this.onSelect.accept(entry.getFileName());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
