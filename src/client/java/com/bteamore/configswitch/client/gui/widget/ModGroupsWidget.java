package com.bteamore.configswitch.client.gui.widget;


import com.bteamore.configswitch.client.gui.widget.entries.ModGroupsEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.List;
import java.util.function.BiConsumer;

public class ModGroupsWidget extends ScrollableListWidget<ModGroupsEntry> {
    public ModGroupsWidget(MinecraftClient minecraftClient, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
    }

    public void addGroup(String modId, List<String> fileNames, boolean selected, int residueCount, BiConsumer<String, Boolean> onToggle, Runnable onClassify) {
        this.addEntry(new ModGroupsEntry(modId, fileNames, selected, residueCount, onToggle, onClassify));
    }

    public void clearGroups() {
        this.clearEntries();
    }
}
