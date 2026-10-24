package com.pixelcatt.simplegraves;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class DatabaseWorker {

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setName("SimpleGraves-DB");
        return thread;
    });

    public ExecutorService getDatabaseExecutor() {
        return databaseExecutor;
    }

    public void shutdown() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                databaseExecutor.shutdownNow();
                databaseExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
