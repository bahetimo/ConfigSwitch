package com.bteamore.configswitch.repo;

public record SourceInfo(String minecraft, String loader) {
    public boolean isKnown() {
        return minecraft != null && !minecraft.isBlank()
            && loader != null && !loader.isBlank();
    }
}
