package com.bteamore.configswitch.client.gui.widget;

import com.bteamore.configswitch.client.gui.widget.entries.CandidateEntry;
import net.minecraft.client.MinecraftClient;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

// 右栏：候选 mod 列表（顺序即相似度排序）,整行可点，点击回调 modId
public class CandidateListWidget extends ScrollableListWidget<CandidateEntry> {

    public CandidateListWidget(MinecraftClient minecraftClient, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
    }

    public void setOnPick(Consumer<String> onPick) {
        this.setOnRowClick(entry -> onPick.accept(entry.getModId()));
    }

    public void addCandidate(String modId, String displayName, BooleanSupplier selected) {
        this.addEntry(new CandidateEntry(modId, displayName, selected));
    }

    public void clearCandidates() {
        this.clearEntries();
    }
}
