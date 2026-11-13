package com.vcs.customvcs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.vcs.customvcs.service.WorkingFileService;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryConcurrencyTest {
    @TempDir Path temporary;

    @Test void fileOperationsWaitForTheSharedMonitor() throws Exception {
        ApiTestWorkspace workspace = new ApiTestWorkspace(temporary.resolve("repositories"));
        var id = workspace.create();
        Path file = workspace.open(id).workingDirectory().resolve("file.txt");
        Files.writeString(file, "before");
        WorkingFileService files = new WorkingFileService(workspace.access);
        CountDownLatch attempted = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            java.util.concurrent.Future<com.vcs.customvcs.dto.FileContentResponse> result;
            synchronized (workspace.access) {
                result = executor.submit(() -> {
                    attempted.countDown();
                    return files.read(id, "file.txt");
                });
                assertTrue(attempted.await(5, TimeUnit.SECONDS));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> result.get(100, TimeUnit.MILLISECONDS));
                Files.writeString(file, "after");
            }
            assertEquals("after", result.get(5, TimeUnit.SECONDS).content());
        }
    }
}
