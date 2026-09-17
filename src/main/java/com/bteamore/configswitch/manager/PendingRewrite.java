package com.bteamore.configswitch.manager;

import java.nio.file.Path;

public record PendingRewrite(Path target, Path source, int hashBeforeFetch) {}