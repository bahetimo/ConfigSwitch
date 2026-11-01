package com.bteamore.configswitch.config;

import com.bteamore.configswitch.core.PackReport;
import com.bteamore.configswitch.util.Log;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class RepoPacker {
    private static final class Reject extends IOException {
        public final PackReport.outcome outcome;
        public final String name;
        public Reject(String message, PackReport.outcome outcome, String name) {
            super(message);
            this.outcome = outcome;
            this.name = name;
        }
    }
    // 包内的条目名：属于包的命名空间，不是文件系统路径，解包端按这两个名字分派落点
    private static final String GLOBAL_ENTRY_DIR = "global";
    private static final String MAPPINGS_ENTRY = "mappings.txt";
    private static final Gson gson = new Gson();

    public static PackReport pack(Path repoRoot, Path mappingsFile, Path output, PackManifest manifest) {
        Path parent = output.toAbsolutePath().getParent();
        Path temp = null;
        int fileCount = 0;
        try {
            temp = Files.createTempFile(parent, "pack", ".tmp");

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(temp))) {
                // Write manifest
                zos.putNextEntry(new ZipEntry(PackManifest.ENTRY_NAME));
                zos.write(gson.toJson(manifest).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // 两条来源分别处理，缺一条就跳过一条，不把整次导出判死
                fileCount += packGlobalDir(zos, repoRoot);
                fileCount += packMappingsFile(zos, mappingsFile);
            }
            // 一条文件都没落进包 = 只有 manifest 的空包：缺一条是正常情况，一条都没有不是
            // （空包导入回来还会顺手删掉 meta，所以这里必须当失败）
            if (fileCount == 0) {
                Log.warn("Nothing to pack: neither {} nor {} has any file",
                    repoRoot.resolve(GLOBAL_ENTRY_DIR), mappingsFile);
                return new PackReport(PackReport.outcome.IO_FAILED, 0, output);
            }
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
        } catch (IOException | UncheckedIOException e) {
            Log.error("Failed to pack repo: ", e);
            return new PackReport(PackReport.outcome.IO_FAILED, 0, output);
        } finally {
            // 失败的打包既不该留下残包，也不该在导出目录里挂着一堆临时文件
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    Log.warn("Failed to delete temp package {}: {}", temp, e);
                }
            }
        }
        return new PackReport(PackReport.outcome.SUCCESS, fileCount, output);
    }

    // repoRoot/global/** → 包里的 global/...：global 不存在就跳过，目录条目照旧写 .../
    private static int packGlobalDir(ZipOutputStream zos, Path repoRoot) throws IOException {
        Path globalDir = repoRoot.resolve(GLOBAL_ENTRY_DIR);
        if (!Files.isDirectory(globalDir)) {
            return 0;
        }

        int fileCount = 0;
        try (var walk = Files.walk(globalDir)) {
            for (Path path : walk.toList()) {
                String entryName = repoRoot.relativize(path).toString().replace("\\", "/");
                if (Files.isDirectory(path)) {
                    entryName += "/";
                }
                zos.putNextEntry(new ZipEntry(entryName));
                if (Files.isRegularFile(path)) {
                    fileCount++;
                    Files.copy(path, zos);
                }
                zos.closeEntry();
            }
        }
        return fileCount;
    }

    // mappingsFile → 包里根部的 mappings.txt：文件不存在就跳过
    private static int packMappingsFile(ZipOutputStream zos, Path mappingsFile) throws IOException {
        if (!Files.isRegularFile(mappingsFile)) {
            return 0;
        }
        zos.putNextEntry(new ZipEntry(MAPPINGS_ENTRY));
        Files.copy(mappingsFile, zos);
        zos.closeEntry();
        return 1;
    }

    public static PackReport unpack(Path repoRoot, Path mappingsFile, Path input) {
        // 两个落点根都先归一化：越界、父级与计划路径的比对都以绝对化后的路径为准
        Path target = repoRoot.toAbsolutePath().normalize();
        Path mappingsTarget = mappingsFile.toAbsolutePath().normalize();
        int fileCount = 0;
        try (ZipFile zipFile = new ZipFile(input.toFile())) {
            // validate schema
            ZipEntry manifestEntry = zipFile.getEntry(PackManifest.ENTRY_NAME);
            if (manifestEntry == null) {
                throw new Reject("Invalid zip file: missing manifest", PackReport.outcome.NOT_A_PACKAGE, input.toString());
            }

            try (InputStream manifestStream = zipFile.getInputStream(manifestEntry)) {
                String manifestJson = new String(manifestStream.readAllBytes(), StandardCharsets.UTF_8);
                PackManifest manifest = gson.fromJson(manifestJson, PackManifest.class);

                if (manifest == null || manifest.format() != PackManifest.DEFAULT_FORMAT) {
                    throw new Reject("Invalid zip file: invalid manifest", PackReport.outcome.NOT_A_PACKAGE, PackManifest.ENTRY_NAME);
                }
            } catch (RuntimeException e) {
                Log.warn("Failed to parse manifest: ", e);
                throw new Reject("Invalid zip file: corrupt manifest", PackReport.outcome.CORRUPTED, PackManifest.ENTRY_NAME);
            }

            // 第一遍只校验：目标现状与本批条目之间的冲突都在写盘之前挡住，不会解到一半才失败
            // 两源共用一套计划路径，跨源撞名（比如 mappings 落点正好被文件条目占住）才能被发现
            Set<Path> plannedPaths = new HashSet<>();
            Set<Path> plannedFiles = new HashSet<>();
            for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().equals(PackManifest.ENTRY_NAME)) {
                    checkEntry(target, mappingsTarget, entry, plannedPaths, plannedFiles);
                }
            }

            // 第二遍才落盘
            for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().equals(PackManifest.ENTRY_NAME) && writeEntry(zipFile, target, mappingsTarget, entry)) {
                    fileCount++;
                }
            }
        } catch (Reject r) {
            // 包自身的问题：只需说明是什么包、哪一条，不打堆栈
            Log.warn("Rejected package {}: {} ({})", input, r.outcome, r.name);
            return new PackReport(r.outcome, fileCount, input);
        } catch (NoSuchFileException e) {
            Log.warn("Rejected package {}: no such file", input);
            return new PackReport(PackReport.outcome.NOT_A_PACKAGE, fileCount, input);
        } catch (ZipException e) {
            Log.warn("Rejected package {}: not a zip archive", input);
            return new PackReport(PackReport.outcome.NOT_A_PACKAGE, fileCount, input);
        } catch (IOException e) {
            Log.error("Failed to unpack repo: ", e);
            return new PackReport(PackReport.outcome.IO_FAILED, fileCount, input);
        }
        return new PackReport(PackReport.outcome.SUCCESS, fileCount, input);
    }

    public static PackReport pack(Path root, Path output, PackManifest manifest) {
        return pack(root, root.resolve("mappings.txt"), output, manifest);
    }

    public static PackReport unpack(Path root, Path input) {
        return unpack(root, root.resolve("mappings.txt"), input);
    }
    public static Optional<Boolean> containsEntry(Path input, String entryName) {
        try (ZipFile zip = new ZipFile(input.toFile())) {
            return Optional.of(zip.getEntry(entryName) != null);
        } catch (RuntimeException | IOException e) {
            Log.warn("Failed to inspect package {}: {}", input, e);
            return Optional.empty();
        }
    }

    // 条目落点：containment 根管这条条目能写到哪儿，outPath 是实际落盘位置
    private record Placement(Path containmentRoot, Path outPath) {
    }

    // 解出的路径必须落在 containment 根之内：zip-slip 只在这里判定
    private static Path resolveInside(Path root, String name) throws IOException {
        Path outPath = root.resolve(name).normalize();
        if (!outPath.startsWith(root)) {
            throw new Reject("Invalid entry, outside target: " + name, PackReport.outcome.CORRUPTED, name);
        }
        return outPath;
    }

    // 条目名正好是 mappings.txt 的落到 mappingsFile，其余都落在 repoRoot 下。
    // 两源的 containment 根不同：mappings.txt 以 mappingsFile 的父目录为根，校验与写入都按这里给的根走，
    // 不能让它套用 repoRoot 的判断绕过越界检查
    private static Placement place(Path repoRoot, Path mappingsFile, String name) throws IOException {
        Path mappingsDir = mappingsFile.getParent();
        if (MAPPINGS_ENTRY.equals(name) && mappingsDir != null) {
            // 条目名固定、落点也就是 mappingsFile，理论上越不了界；仍走同一套判定，改动时不会悄悄破掉防护
            return new Placement(mappingsDir, resolveInside(mappingsDir, mappingsFile.getFileName().toString()));
        }
        return new Placement(repoRoot, resolveInside(repoRoot, name));
    }

    // 条目校验：与目标现状冲突、与本批已计划条目冲突、目标不可写都在这里挡住
    private static void checkEntry(Path repoRoot, Path mappingsFile, ZipEntry entry, Set<Path> plannedPaths, Set<Path> plannedFiles) throws IOException {
        String name = entry.getName();
        Placement placed = place(repoRoot, mappingsFile, name);
        Path root = placed.containmentRoot();
        Path outPath = placed.outPath();

        // 父级链上不能有普通文件挡路：目标里的旧文件，或本批已计划的文件条目（另一源的落点也算）
        // 一直看到 containment 根本身：mappings.txt 的目标就是根下的文件，根被文件占位时写盘必败
        Path parent = outPath.getParent();
        while (parent != null && parent.startsWith(root)) {
            if (Files.exists(parent) && !Files.isDirectory(parent)) {
                throw new Reject("Invalid entry, parent is a file: " + name, PackReport.outcome.IO_FAILED, name);
            }
            if (plannedFiles.contains(parent)) {
                throw new Reject("Invalid entry, conflicts with an earlier entry: " + name, PackReport.outcome.CORRUPTED, name);
            }
            parent = parent.getParent();
        }

        if (entry.isDirectory()) {
            if (Files.exists(outPath) && !Files.isDirectory(outPath)) {
                throw new Reject("Invalid entry, target is a file: " + name, PackReport.outcome.IO_FAILED, name);
            }
            if (plannedFiles.contains(outPath)) {
                throw new Reject("Invalid entry, conflicts with an earlier entry: " + name, PackReport.outcome.CORRUPTED, name);
            }
            plannedPaths.add(outPath);
            return;
        }

        if (Files.isDirectory(outPath)) {
            throw new Reject("Invalid entry, target is a directory: " + name, PackReport.outcome.IO_FAILED, name);
        }
        if (Files.exists(outPath) && !Files.isWritable(outPath)) {
            throw new Reject("Invalid entry, target is not writable: " + name, PackReport.outcome.IO_FAILED, name);
        }
        // 同名处已计划为目录，或已有条目要落在这个文件下面
        if ((plannedPaths.contains(outPath) && !plannedFiles.contains(outPath))
                || plannedPaths.stream().anyMatch(planned -> !planned.equals(outPath) && planned.startsWith(outPath))) {
            throw new Reject("Invalid entry, conflicts with an earlier entry: " + name, PackReport.outcome.CORRUPTED, name);
        }
        plannedPaths.add(outPath);
        plannedFiles.add(outPath);
    }

    // 写入：先写同目录临时文件再换入，中途失败也不会留下半截文件；返回是否真的写了一个文件
    private static boolean writeEntry(ZipFile zipFile, Path repoRoot, Path mappingsFile, ZipEntry entry) throws IOException {
        Path outPath = place(repoRoot, mappingsFile, entry.getName()).outPath();
        if (entry.isDirectory()) {
            Files.createDirectories(outPath);
            return false;
        }

        Path parent = outPath.getParent();
        Files.createDirectories(parent);

        Path temp = Files.createTempFile(parent, "unpack", ".tmp");
        try {
            // 每个 entry 的输入流要独立关闭
            try (InputStream in = zipFile.getInputStream(entry)) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(temp, outPath);
            temp = null;
        } finally {
            if (temp != null) {
                Files.deleteIfExists(temp);
            }
        }
        return true;
    }

    // 不支持原子移动就降级普通移动
    private static void moveIntoPlace(Path temp, Path outPath) throws IOException {
        try {
            Files.move(temp, outPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, outPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
