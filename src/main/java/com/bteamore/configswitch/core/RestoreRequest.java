package com.bteamore.configswitch.core;

import com.bteamore.configswitch.discovery.BackupSnapshot;

import java.nio.file.Path;
import java.util.function.Consumer;

public record RestoreRequest(BackupSnapshot snapshot, Path targetRoot, Path backupRoot, Consumer<SyncReport> onResult) {
}
