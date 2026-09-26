package com.bteamore.configswitch.client.gui.widget;

import com.bteamore.configswitch.client.gui.widget.entries.CandidateEntry;
import net.minecraft.client.MinecraftClient;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// 右栏：候选 mod 列表（顺序即相似度排序）,整行可点，点击回调 modId
public class CandidateListWidget extends ScrollableListWidget<CandidateEntry> {
    private Consumer<String> onPick = modId -> {
    };

    public CandidateListWidget(MinecraftClient minecraftClient, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
    }

    public void setOnPick(Consumer<String> onPick) {
        this.onPick = onPick;
    }

    public void addCandidate(String modId, String displayName, BooleanSupplier selected) {
        this.addEntry(new CandidateEntry(modId, displayName, selected));
    }

    public void clearCandidates() {
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
            CandidateEntry entry = this.getEntryAtPosition(mouseX, mouseY);
            if (entry != null) {
                this.onPick.accept(entry.getModId());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
