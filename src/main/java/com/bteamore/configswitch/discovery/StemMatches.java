package com.bteamore.configswitch.discovery;

public class StemMatches {
    public static boolean matches(String modId, String stem) {
        // 分离(-_.)长前缀依次比较
        String current = stem.toLowerCase();
        while (true) {
            if (modId.equalsIgnoreCase(current)) {
                return true;
            }

            int dot = Math.max(Math.max(current.lastIndexOf("-"), current.lastIndexOf("_")), current.lastIndexOf("."));
            if (dot < 0) {
                return false;
            }
            current = current.substring(0, dot);
        }
    }
}
