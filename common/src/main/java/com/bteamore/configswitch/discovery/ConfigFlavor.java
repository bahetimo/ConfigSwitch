package com.bteamore.configswitch.discovery;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigFlavor {
    private ConfigFlavor() {
    }
    private static final Set<String> FLAVORS = Set.of("common", "client", "server", "config", "settings", "blacklist", "whitelist", "default", "options", "main", "core", "global", "general");
    private static final Pattern PATTERN = Pattern.compile("[A-Z]");

    public static boolean isConfigLike(String fileName, String dirName){
        if (fileName == null || dirName == null || dirName.isEmpty() || fileName.isEmpty()){
            return false;
        }
        String acronym = acronym(dirName).toLowerCase();

        fileName = fileName.toLowerCase();
        dirName = dirName.toLowerCase();

        if (fileName.contains(dirName)){
            return true;
        }
        if (!acronym.isEmpty() && fileName.contains(acronym)){
            return true;
        }
        if (FLAVORS.stream().anyMatch(fileName::contains)){
            return true;
        }

        return false;
    }

    // 后续再提取
    private static String acronym(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String cleaned = name.replaceAll("[(\\[{].*?[)\\]}]", "")
                .replaceAll("\\s+", " ")
                .trim();

        String[] wordBlocks = Arrays.stream(cleaned.split("[^a-zA-Z0-9]"))
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);

        StringBuilder sb = new StringBuilder();
        for (String block : wordBlocks) {
            Matcher matcher = PATTERN.matcher(block);
            List<String> upperChars = new ArrayList<>();
            while (matcher.find()) {
                upperChars.add(matcher.group());
            }

            if (upperChars.isEmpty()) {
                sb.append(Character.toUpperCase(block.charAt(0)));
            } else {
                if (Character.isLowerCase(block.charAt(0))) {
                    sb.append(Character.toUpperCase(block.charAt(0)));
                }
                upperChars.forEach(sb::append);
            }
        }

        String acron = sb.toString();
        return acron.length() < 2 ? "" : acron;
    }
}
