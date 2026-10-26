package com.bteamore.configswitch.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class Time {
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss");
    public static final Pattern BACKUP_FILE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}--\\d{2}-\\d{2}-\\d{2}");

    // 格式：YYYY-MM-DD--HH-mm-ss
    public static String timeString() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }
}
