package com.bteamore.configswitch.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Log {
    public static final String NAME = "configswitch";
    private static boolean DEBUG_ON;
    private static Path LOG_PATH;
    private static final DateTimeFormatter TIME_PATTERN = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NAME);

    private Log() {
    }

    public static void init(Path logsDir) {

        LOG_PATH = logsDir.resolve("configswitch-debug.log");
        Path prev = logsDir.resolve("configswitch-debug.prev.log");

        boolean debug = Boolean.getBoolean("configswitch.debug");
        if (debug){
            try {
                if (!Files.exists(logsDir)){
                    Files.createDirectories(logsDir);
                }
                if (Files.exists(LOG_PATH)) {
                    Files.move(LOG_PATH, prev, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                LOG.warn("[ConfigSwitch] Failed to move log file, and debug mode is disabled", e);
                debug = false;
            }
        }
        DEBUG_ON = debug;
    }

    public static void info(String message, Object... args) {
        LOG.info("[ConfigSwitch] " + message, args);
        writeFile(Level.INFO, message, args);
    }

    public static void warn(String message, Object... args) {
        LOG.warn("[ConfigSwitch] " + message, args);
        writeFile(Level.WARN, message, args);
    }

    public static void error(String message, Object... args) {
        LOG.error("[ConfigSwitch] " + message, args);
        writeFile(Level.ERROR, message, args);
    }

    public static void debug(String message, Object... args) {
        LOG.debug("[ConfigSwitch] " + message, args);
        writeFile(Level.DEBUG, message, args);
    }

    private static void writeFile(Level level, String message, Object... args) {
        if (!DEBUG_ON || LOG_PATH == null) {
            return;
        }

        ParameterizedMessage pm = new ParameterizedMessage(message, args);
        String formattedMessage = pm.getFormattedMessage();
        Throwable t = pm.getThrowable();

        String line = String.format("[%s] [%s] %s%n", LocalTime.now().format(TIME_PATTERN), level, formattedMessage);

        String text = line;

        try {
            if (t != null) {
                StringWriter stringWriter = new StringWriter();
                t.printStackTrace(new PrintWriter(stringWriter));
                String stackTrace = stringWriter.toString();
                text += stackTrace;
            }
            Files.writeString(LOG_PATH, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignore) {
        }
    }
}
