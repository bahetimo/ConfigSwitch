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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final List<String> WHITE_LIST = List.of(
            "global",
            "mappings.txt"
    );
    private static final Gson gson = new Gson();

    public static PackReport pack(Path root, Path output, PackManifest manifest) {
        Path parent = output.toAbsolutePath().getParent();
        Path temp;
        int fileCount = 0;
        try {
            temp = Files.createTempFile(parent, "pack", ".tmp");

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(temp))) {
                // Write manifest
                zos.putNextEntry(new ZipEntry(PackManifest.ENTRY_NAME));
                zos.write(gson.toJson(manifest).getBytes(StandardCharsets.UTF_8));

                // Add white-listed files
                for (String whiteList : WHITE_LIST) {
                    Path file = root.resolve(whiteList);
                    if (Files.isDirectory(file)) {
                        try (var walk = Files.walk(file)) {
                            for (Path path : walk.toList()) {
                                String entryName = root.relativize(path).toString().replace("\\", "/");
                                if (Files.isDirectory(path)) {
                                    entryName += "/";
                                }
                                try {
                                    zos.putNextEntry(new ZipEntry(entryName));
                                    if (Files.isRegularFile(path)) {
                                        fileCount++;
                                        Files.copy(path, zos);
                                    }
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }
                        }
                    } else {
                        fileCount++;
                        zos.putNextEntry(new ZipEntry(whiteList));
                        Files.copy(file, zos);
                        zos.closeEntry();
                    }
                }
            }
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UncheckedIOException e) {
            Log.error("Failed to pack repo: ", e);
            return new PackReport(PackReport.outcome.IO_FAILED, 0, output);
        }
        return new PackReport(PackReport.outcome.SUCCESS, fileCount, output);
    }

    public static PackReport unpack(Path root, Path input) {
        // 目标根先归一化，越界与父级判断都以它为准
        Path target = root.toAbsolutePath().normalize();
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
            Set<Path> plannedPaths = new HashSet<>();
            Set<Path> plannedFiles = new HashSet<>();
            for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().equals(PackManifest.ENTRY_NAME)) {
                    checkEntry(target, entry, plannedPaths, plannedFiles);
                }
            }

            // 第二遍才落盘
            for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().equals(PackManifest.ENTRY_NAME) && writeEntry(zipFile, target, entry)) {
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

    // 解出的路径必须落在 target 之内：zip-slip 只在这里判定
    private static Path resolveInside(Path target, String name) throws IOException {
        Path outPath = target.resolve(name).normalize();
        if (!outPath.startsWith(target)) {
            throw new Reject("Invalid entry, outside target: " + name, PackReport.outcome.CORRUPTED, name);
        }
        return outPath;
    }

    // 条目校验：与目标现状冲突、与本批已计划条目冲突、目标不可写都在这里挡住
    private static void checkEntry(Path target, ZipEntry entry, Set<Path> plannedPaths, Set<Path> plannedFiles) throws IOException {
        String name = entry.getName();
        Path outPath = resolveInside(target, name);

        // 父级链上不能有普通文件挡路：目标里的旧文件，或本批已计划的文件条目
        Path parent = outPath.getParent();
        while (parent != null && !parent.equals(target) && parent.startsWith(target)) {
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
    private static boolean writeEntry(ZipFile zipFile, Path target, ZipEntry entry) throws IOException {
        Path outPath = resolveInside(target, entry.getName());
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
