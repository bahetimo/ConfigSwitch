package com.bteamore.configswitch.discovery;

public final class FileNames {
    private FileNames() {
    }

    public static String stemOf(String name) {
        if (name.endsWith("/")){
            return name.substring(0, name.length() - 1);
        }
        int dot = name.lastIndexOf('.');
        return (dot < 0) ? name : name.substring(0, dot);
    }
}
