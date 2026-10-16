package com.bteamore.configswitch.config;

public record PackManifest(int format, String exportedAt, String  context) {
    public static final int DEFAULT_FORMAT = 1;
    public static final String ENTRY_NAME = "configswitch-export.json";
}
