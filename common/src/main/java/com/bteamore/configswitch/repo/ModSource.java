package com.bteamore.configswitch.repo;

public record ModSource(String minecraft, String loader, String at) {
    public ModSource {
        minecraft = (minecraft == null) ? "" : minecraft;
        loader = (loader == null) ? "" : loader;
        at = (at == null) ? "" : at;
    }
}
