package com.bteamore.configswitch.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Time {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss");

    public static long now() {
        return System.currentTimeMillis();
    }

    // 格式：YYYY-MM-DD--HH-mm-ss
    public static String timeString() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }
}
