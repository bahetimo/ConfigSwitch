package com.bteamore.configswitch.core;

import com.bteamore.configswitch.discovery.BackupSnapshot;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

public record RestoreRequest(BackupSnapshot snapshot, Function<Path, Path> targetResolver, Path backupRoot, Consumer<SyncReport> onResult) {
}
