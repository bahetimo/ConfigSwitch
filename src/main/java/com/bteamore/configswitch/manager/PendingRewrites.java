package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.util.Hash;

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

    public static void flush() {
        for (PendingRewrite rewrite : INSTANCE) {
            Path from = rewrite.source();
            Path to = rewrite.target();

            int nowHash = Hash.hashOf(to);
            if (rewrite.hashBeforeFetch() != 0 && nowHash == rewrite.hashBeforeFetch()){
                System.out.println("File changed while stoping: " + rewrite.target());

                // 先吧copy逻辑复制过来，后续在封装到util
                try {
                    Path temp = Files.createTempFile(to.getParent(), to.getFileName().toString(), ".tmp");
                    Files.copy(from, temp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    // LOGGER此时已被关闭
                    e.printStackTrace();
                }
            }
        }
    }
}
