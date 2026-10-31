package com.bteamore.configswitch.core;

import com.bteamore.configswitch.discovery.BackupSnapshot;

import java.nio.file.Path;
import java.util.function.Consumer;

public record RestoreRequest(BackupSnapshot snapshot, RestoreTarget restoreTarget, Path backupRoot, Consumer<SyncReport> onResult) {
}
