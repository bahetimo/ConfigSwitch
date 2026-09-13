package com.bteamore.configswitch.core;

import com.bteamore.configswitch.repo.ConfigPaths;

import java.util.List;
import java.util.function.Consumer;

public record SyncRequest(List<ConfigPaths> paths, Consumer<SyncReport> callback) {
}
