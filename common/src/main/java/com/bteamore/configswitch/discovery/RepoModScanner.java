package com.bteamore.configswitch.discovery;

import com.bteamore.configswitch.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class RepoModScanner {
    public Set<String> scan(Path commonDir){
        if (commonDir == null || Files.notExists(commonDir)) {
            return Collections.emptySet(); // 首次运行 空结果，不报错
        }

        Set<String> result = new HashSet<>();
        try (Stream<Path> entries = Files.list(commonDir)) {
            entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.equals(ModGroup.UNCATEGORIZED_ID)) // uncategorized 不是modid
                    .forEach(result::add);
        } catch (IOException e) {
            Log.warn("Failed to scan common directory {}", commonDir, e);
            return Collections.emptySet();
        }
        return result;
    }
}
