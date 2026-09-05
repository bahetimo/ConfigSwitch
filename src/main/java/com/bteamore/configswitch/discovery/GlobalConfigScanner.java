package com.bteamore.configswitch.discovery;

import com.bteamore.configswitch.Configswitch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class GlobalConfigScanner {

    // 遍历common/ ，对<dirName>/ 的<dirName>为modid，其子文件为该modid的配置文件，options.txt为原版配置，与<dirName>/同级
    public List<ModGroup> scan(Path commonDir){
        List<ModGroup> result = new ArrayList<>();
        if (commonDir == null || Files.notExists(commonDir)) {
            return result; // 首次运行 空结果，不报错
        }
        if (Files.exists(commonDir.resolve("options.txt"))){
            ModGroup vanilla = new ModGroup(ModGroup.VANILLA_ID);
            vanilla.addFile(commonDir.resolve("options.txt"));
            result.add(vanilla);
        }

        try (Stream<Path> entries = Files.list(commonDir)) {
            entries.filter(Files::isDirectory)
                    .map(this::toModGroup)
                    .filter(Objects::nonNull)
                    .forEach(result::add);
        } catch (IOException e) {
            Configswitch.LOGGER.error("扫描 common 目录失败 {}: {}", commonDir, e.getMessage());
        }
        return result;
    }

    private ModGroup toModGroup(Path modDir) {
        String modId = modDir.getFileName().toString();
        if (ModGroup.VANILLA_ID.equals(modId) || ModGroup.UNCATEGORIZED_ID.equals(modId)) {
            Configswitch.LOGGER.warn("跳过保留 ID 目录: {}", modDir);
            return null;
        }
        ModGroup group = new ModGroup(modId);

        try (Stream<Path> entries = Files.list(modDir)) {
            entries.filter(Files::isRegularFile)
                    .forEach(group::addFile);
        } catch (IOException e) {
            Configswitch.LOGGER.error("扫描 mod 目录失败 {}: {}", modDir, e.getMessage());
        }
        return group;
    }
}
