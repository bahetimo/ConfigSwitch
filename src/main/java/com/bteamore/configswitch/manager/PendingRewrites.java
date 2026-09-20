package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.util.Hash;
import com.bteamore.configswitch.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class PendingRewrites {
    private static final List<PendingRewrite> INSTANCE = new ArrayList<>();

    public static void add(Path target, Path source, int hashBeforeFetch) {
        PendingRewrite pendingRewrite = new PendingRewrite(target, source, hashBeforeFetch);
        INSTANCE.add(pendingRewrite);
    }

    // Logger此时可能已被关闭
    public static void flush() {
        for (PendingRewrite rewrite : INSTANCE) {
            Path from = rewrite.source();
            Path to = rewrite.target();

            int nowHash = Hash.hashOf(to);
            if (rewrite.hashBeforeFetch() != 0 && nowHash == rewrite.hashBeforeFetch()){
                Log.debug("File changed while stopping: {}", rewrite.target());

                try {
                    Path temp = Files.createTempFile(to.getParent(), to.getFileName().toString(), ".tmp");
                    Files.copy(from, temp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    // LOGGER此时可能已被关闭，不一定能记录日志
                    Log.error("Failed to restore file: {}", to);
                }
            }
        }
    }
}
