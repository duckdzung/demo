package com.zalopay.demo.controller;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system-tuning")
public class SystemTuningController {

    private static final Logger logger = LoggerFactory.getLogger(SystemTuningController.class);

    private static final List<byte[]> holder = new ArrayList<>();

    private final ExecutorService exec = Executors.newCachedThreadPool();

    @PostMapping("/ram")
    public ResponseEntity<Map<String, Object>> allocateRam(@RequestParam int sizeMB) {
        long maxHeapMB = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / (1024 * 1024);
        long usedHeapMB = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long freeHeapMB = maxHeapMB - usedHeapMB;

        if (sizeMB <= 0 || sizeMB > freeHeapMB) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "sizeMB must be >0 và ≤" + freeHeapMB + " MB"));
        }

        try {
            long before = usedHeapMB;

            long totalBytes = (long) sizeMB * 1024 * 1024;
            int chunkSize = 1024 * 1024; // 1 MiB
            int chunks = (int) (totalBytes / chunkSize);
            int rem = (int) (totalBytes % chunkSize);

            for (int i = 0; i < chunks; i++) {
                holder.add(new byte[chunkSize]);
            }
            if (rem > 0) {
                holder.add(new byte[rem]);
            }

            Thread.sleep(30_000);

            long after = ManagementFactory.getMemoryMXBean()
                    .getHeapMemoryUsage().getUsed() / (1024 * 1024);

            return ResponseEntity.ok(Map.of(
                    "memoryBeforeMB", before,
                    "memoryAfterMB", after));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(Map.of("error", "Interrupted"));
        } catch (OutOfMemoryError oom) {
            return ResponseEntity.status(500).body(Map.of("error", "OOM: quá lớn"));
        }
    }

    @PostMapping("/cpu")
    public ResponseEntity<?> stressCpu(@RequestParam(defaultValue="30") int seconds) {
        if (seconds < 1 || seconds > 60) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Duration must be 1–60s"));
        }

        int cores = Runtime.getRuntime().availableProcessors();
        long end = System.currentTimeMillis() + seconds * 1000L;
        for (int i = 0; i < cores; i++) {
            exec.submit(() -> {
                while (System.currentTimeMillis() < end) {
                    Math.sqrt(Math.random());
                }
            });
        }

        return ResponseEntity.ok(Map.of(
                "message", "Started CPU stress for " + seconds + "s on " + cores + " cores"
        ));
    }
}
