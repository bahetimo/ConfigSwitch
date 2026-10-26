package com.bteamore.configswitch.discovery;

import com.bteamore.configswitch.repo.RepoPaths;

public final class MappingConfig {
    private static final MappingsStore STORE = new MappingsStore(RepoPaths.root().resolve("mappings.txt"));
    private static MappingTable table = MappingTable.empty();

    public static MappingTable mapping() {
        return table;
    }

    public static void injectSeed(){
        MappingTable injectSeed = STORE.injectSeed(MappingTable.loadBundled().entries());

        table = injectSeed;
    }

    public static void reload(){
        table = STORE.load();
    }

    public static MappingsStore store() {
        return STORE;
    }
}
