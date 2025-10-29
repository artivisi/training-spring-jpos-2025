package com.example.atm;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.BASE24Packager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load test client to test queue monitoring and throttling.
 * Sends multiple requests concurrently to trigger queue buildup.
 */
public class LoadTestClient {

    private static final String HOST = "localhost";
    private static final int PORT = 8000;
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        int numThreads = 20;
        int requestsPerThread = 10;

        if (args.length >= 1) {
            numThreads = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            requestsPerThread = Integer.parseInt(args[1]);
        }

        System.out.println("Starting load test:");
        System.out.println("  Threads: " + numThreads);
        System.out.println("  Requests per thread: " + requestsPerThread);
        System.out.println("  Total requests: " + (numThreads * requestsPerThread));
        System.out.println();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        long startTime = System.currentTimeMillis();

        final int finalRequestsPerThread = requestsPerThread;
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < finalRequestsPerThread; j++) {
                    try {
                        sendRequest(threadId, j);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        System.err.println("Thread " + threadId + ", Request " + j + " failed: " + e.getMessage());
                    }

                    // Small delay between requests from same thread
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println();
        System.out.println("Load test completed:");
        System.out.println("  Duration: " + duration + "ms");
        System.out.println("  Successful: " + successCount.get());
        System.out.println("  Failed: " + failureCount.get());
        System.out.println("  Throughput: " + (successCount.get() * 1000.0 / duration) + " req/s");
    }

    private static void sendRequest(int threadId, int requestId) throws IOException, ISOException {
        ASCIIChannel channel = null;
        try {
            channel = new ASCIIChannel(HOST, PORT, new BASE24Packager());
            channel.setTimeout(30000); // 30 second timeout
            channel.connect();

            ISOMsg request = new ISOMsg();
            request.setMTI("0200");

            // Balance Inquiry
            request.set(2, "4000001234" + String.format("%06d", threadId * 1000 + requestId)); // PAN
            request.set(3, "310000"); // Processing code - Balance Inquiry
            request.set(11, String.format("%06d", threadId * 1000 + requestId)); // STAN
            request.set(41, "ATM" + String.format("%05d", threadId)); // Terminal ID
            request.set(49, "360"); // Currency code

            channel.send(request);

            ISOMsg response = channel.receive();

            String responseCode = response.getString(39);
            if (!"00".equals(responseCode)) {
                System.err.println("Thread " + threadId + ", Request " + requestId +
                    " received error response: " + responseCode);
            } else {
                System.out.println("Thread " + threadId + ", Request " + requestId + " succeeded");
            }
        } finally {
            if (channel != null && channel.isConnected()) {
                try {
                    channel.disconnect();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
