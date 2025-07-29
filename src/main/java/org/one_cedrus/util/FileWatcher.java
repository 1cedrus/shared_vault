package org.one_cedrus.util;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import org.one_cedrus.shared.FileChange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FileWatcher {
    private final Path folderPath;
    private final Consumer<List<FileChange>> onFileChange;
    private final int debounceSeconds;
    private DirectoryWatcher watcher;
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Map<Path, DirectoryChangeEvent> pendingChanges = new HashMap<>();
    private ScheduledFuture<?> applyChangeTask;

    public FileWatcher(Path folderPath, Consumer<List<FileChange>> onFileChange, int debounceSeconds) {
        this.folderPath = folderPath;
        this.onFileChange = onFileChange;
        this.debounceSeconds = debounceSeconds;
    }

    public void start() throws IOException {
        this.watcher = DirectoryWatcher.builder()
                .path(folderPath)
                .listener(this::handleDirectoryEvent)
                .build();

        watcher.watchAsync();
    }

    public void pause() throws IOException {
        watcher.close();
    }

    public void stop() throws Exception {
        watcher.close();
        debounceExecutor.shutdown();

        try {
            if (!debounceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                debounceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            debounceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void handleDirectoryEvent(DirectoryChangeEvent event) {
        Path changedPath = event.path();

        // Ignore .sv directory and other ignored paths
        if (isIgnoredPath(changedPath)) {
            return;
        }

        System.out.println("[DEBUG]: File change detected: " + event.eventType() + " - " + changedPath);

        pendingChanges.put(changedPath, event);

        if (applyChangeTask != null) {
            applyChangeTask.cancel(false);
        }

        // Schedule debounced processing
        applyChangeTask = debounceExecutor.schedule(() -> {
            List<FileChange> changes = pendingChanges.entrySet().stream().map(entry -> {
                String relativePath = folderPath.relativize(entry.getKey()).toString().replace("\\", "/");

                switch (entry.getValue().eventType()) {
                    case CREATE:
                    case MODIFY:
                        try {
                            if (Files.exists(entry.getKey()) && Files.isRegularFile(entry.getKey())) {
                                String hash = VaultUtils.calculateFileHash(entry.getKey().toFile());
                                return new FileChange(relativePath, hash,
                                        entry.getValue().eventType() == DirectoryChangeEvent.EventType.CREATE ? "ADDED"
                                                : "MODIFIED");
                            }
                        } catch (Exception ignored) {
                        }
                        break;

                    case DELETE:
                        return new FileChange(relativePath, null, "DELETED");

                    default:
                        System.out.println("[DEBUG]: Won't handle that case " + event.eventType());
                }

                return null;
            }).toList();

            pendingChanges.clear();

            onFileChange.accept(changes);
        }, debounceSeconds, TimeUnit.SECONDS);
    }

    private boolean isIgnoredPath(Path path) {
        String pathStr = path.toString();
        return pathStr.contains(".sv") ||
                pathStr.contains(".git") ||
                pathStr.contains(".DS_Store");
    }
}