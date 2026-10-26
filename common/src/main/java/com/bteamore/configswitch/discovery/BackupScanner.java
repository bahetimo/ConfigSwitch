package com.bteamore.configswitch.discovery;

import com.bteamore.configswitch.util.Log;
import com.bteamore.configswitch.util.Time;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class BackupScanner {
    public List<BackupSnapshot> scan(Path backupRoot) {
        List<BackupSnapshot> result = new ArrayList<>();
        if (backupRoot == null || Files.notExists(backupRoot)) {
            return result;
        }
        try (var stream = Files.list(backupRoot)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> Time.BACKUP_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                    .map(this::scanSnapshot)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(BackupSnapshot::timeStamp).reversed())
                    .forEach(result::add);
        } catch (IOException e) {
            Log.error("Failed to scan backup directory {}", backupRoot, e);
        }
        return result;
    }

    private BackupSnapshot scanSnapshot(Path snapshotRoot) {
        try (var stream = Files.walk(snapshotRoot)) {
            List<Path> list = stream.filter(Files::isRegularFile)
                    .map(snapshotRoot::relativize)
                    .toList();
            return new BackupSnapshot(snapshotRoot.getFileName().toString(), list);
        } catch (IOException e) {
            Log.error("Failed to scan snapshot {}", snapshotRoot, e);
            return null;
        }
    }
}
