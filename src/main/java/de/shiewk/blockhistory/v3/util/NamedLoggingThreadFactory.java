package de.shiewk.blockhistory.v3.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamedLoggingThreadFactory implements ThreadFactory {

    private final String threadName;
    private final int threadPriority;
    private final Logger logger;
    private final String label;
    private final AtomicInteger threadNumber;

    public NamedLoggingThreadFactory(String threadName, int threadPriority, Logger logger, String label, AtomicInteger threadNumber) {
        this.threadName = threadName;
        this.threadPriority = threadPriority;
        this.logger = logger;
        this.label = label;
        this.threadNumber = threadNumber;
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread thread = new Thread(r);
        thread.setName(threadName + "-" + threadNumber.incrementAndGet());
        thread.setPriority(threadPriority);
        logger.info("Created new thread for {}: {}", label, thread);
        return thread;
    }

}
