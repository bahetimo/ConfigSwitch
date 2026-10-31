package com.bteamore.configswitch.core;

import java.nio.file.Path;
import java.util.function.Function;

public record RestoreTarget(Function<Path, Path> targetResolver, boolean global) {
}
