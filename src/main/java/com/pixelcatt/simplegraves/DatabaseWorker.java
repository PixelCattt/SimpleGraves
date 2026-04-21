package com.pixelcatt.simplegraves;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


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
    }
}