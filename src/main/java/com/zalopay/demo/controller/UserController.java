package com.zalopay.demo.controller;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zalopay.demo.model.User;
import com.zalopay.demo.repository.UserRepository;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private static final int TOTAL_RECORDS = 10_000_000;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            user.setName(userDetails.getName());
            return userRepository.save(user);
        }
        return null;
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
    }

    @DeleteMapping("/clear")
    public Map<String, Object> clearAllUsers() {
        long count = userRepository.count();
        userRepository.deleteAll();
        logger.info("Cleared {} users from database", count);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deletedCount", count);
        result.put("message", "All users cleared");
        return result;
    }

    @PostMapping("/bulk-insert")
    public Map<String, Object> bulkInsertUsers(
            @RequestParam(defaultValue = "5000") int batchSize,
            @RequestParam(defaultValue = "200") int threadPoolSize) {

        logger.info("Starting bulk insert of {} users with batchSize={}, threadPoolSize={}",
                TOTAL_RECORDS, batchSize, threadPoolSize);

        long startTime = System.currentTimeMillis();
        AtomicInteger processedRecords = new AtomicInteger(0);
        AtomicInteger batchCounter = new AtomicInteger(0);
        ExecutorService threadPool = Executors.newFixedThreadPool(threadPoolSize);
        List<Future<Integer>> futures = new ArrayList<>();

        // Get current max ID from database to avoid conflicts
        long startingId = getCurrentMaxId() + 1;
        logger.info("Starting ID generation from: {}", startingId);

        try {
            // Submit batches to thread pool
            for (int i = 0; i < TOTAL_RECORDS; i += batchSize) {
                final int startIndex = i;
                final int currentBatchSize = Math.min(batchSize, TOTAL_RECORDS - i);
                final int batchNumber = (i / batchSize) + 1;
                final long batchStartingId = startingId + startIndex;

                Future<Integer> future = threadPool.submit(() -> {
                    long batchStartTime = System.currentTimeMillis();
                    try (Connection connection = dataSource.getConnection()) {
                        connection.setAutoCommit(false);

                        StringBuilder sql = new StringBuilder("INSERT INTO users (id, name) VALUES");
                        for (int j = 0; j < currentBatchSize; j++) {
                            if (j > 0)
                                sql.append(", ");
                            long userId = batchStartingId + j;
                            String userName = "user" + userId;
                            sql.append("(").append(userId).append(", '").append(userName).append("')");
                        }

                        try (var statement = connection.createStatement()) {
                            statement.executeUpdate(sql.toString());
                            connection.commit();
                        }

                        long batchDuration = System.currentTimeMillis() - batchStartTime;
                        int completedBatches = batchCounter.incrementAndGet();

                        logger.info("Batch {}/{} completed by thread {} - {} records (ID: {}-{}) in {} ms",
                                completedBatches, (TOTAL_RECORDS + batchSize - 1) / batchSize,
                                Thread.currentThread().getName(), currentBatchSize,
                                batchStartingId, batchStartingId + currentBatchSize - 1, batchDuration);

                        return currentBatchSize;
                    } catch (Exception e) {
                        logger.error("Error in batch {}: {}", batchNumber, e.getMessage());
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);
            }

            // Collect results
            List<String> errors = new ArrayList<>();
            for (Future<Integer> future : futures) {
                try {
                    processedRecords.addAndGet(future.get());
                } catch (Exception e) {
                    errors.add(e.getMessage());
                }
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            long endingId = startingId + processedRecords.get() - 1;

            Map<String, Object> result = new HashMap<>();
            result.put("success", errors.isEmpty());
            result.put("totalRecords", TOTAL_RECORDS);
            result.put("processedRecords", processedRecords.get());
            result.put("batchSize", batchSize);
            result.put("threadPoolSize", threadPoolSize);
            result.put("totalDurationMs", totalDuration);
            result.put("recordsPerSecond", (processedRecords.get() * 1000.0) / totalDuration);
            result.put("startingId", startingId);
            result.put("endingId", endingId);
            result.put("idRange", startingId + "-" + endingId);

            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }

            logger.info("Bulk insert completed! Processed {}/{} records (ID: {}-{}) in {} ms - {} records/second",
                    processedRecords.get(), TOTAL_RECORDS, startingId, endingId, totalDuration,
                    String.format("%.2f", (processedRecords.get() * 1000.0) / totalDuration));

            return result;

        } catch (Exception e) {
            logger.error("Error during bulk insert: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("processedRecords", processedRecords.get());
            return errorResult;
        } finally {
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
        }
    }

    // Helper method to get current maximum ID from database
    private long getCurrentMaxId() {
        try (Connection connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                var resultSet = statement.executeQuery("SELECT COALESCE(MAX(id), 0) FROM user");
                if (resultSet.next()) {
                    long maxId = resultSet.getLong(1);
                    logger.info("Current max ID in database: {}", maxId);
                    return maxId;
                }
            }
        } catch (Exception e) {
            logger.warn("Could not get max ID from database, starting from 0: {}", e.getMessage());
        }
        return 0;
    }
}
