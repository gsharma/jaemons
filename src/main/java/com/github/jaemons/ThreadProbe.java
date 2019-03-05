package com.github.jaemons;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A daemon that continually probes a JVM's thread usage.
 * 
 * @author gaurav
 */
public final class ThreadProbe extends Thread {
  private static final Logger logger = LogManager.getLogger(ThreadProbe.class.getSimpleName());
  private final long probeMillis;
  private final AtomicReference<ThreadsSnapshot> threadsSnapshot = new AtomicReference<>();
  private final List<State> statesOfInterest = new ArrayList<>();

  public ThreadProbe(final long probeMillis, final List<State> threadStatesOfInterest) {
    this.probeMillis = probeMillis;
    if (threadStatesOfInterest != null) {
      statesOfInterest.addAll(threadStatesOfInterest);
    } else {
      statesOfInterest.addAll(Arrays.asList(State.values()));
    }
    ManagementFactory.getThreadMXBean().setThreadCpuTimeEnabled(true);
    ManagementFactory.getThreadMXBean().setThreadContentionMonitoringEnabled(true);
    setDaemon(true);
    setName("threads-probe");
    start();
  }

  @Override
  public void run() {
    logger.info("Initialized probe");
    try {
      while (!isInterrupted()) {
        final ThreadsSnapshot threadsSnapshot = new ThreadsSnapshot();
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        final int currentThreadCount = threadMXBean.getThreadCount();
        final int daemonThreadCount = threadMXBean.getDaemonThreadCount();
        final int peakThreadCount = threadMXBean.getPeakThreadCount();
        final long totalStartedThreadCount = threadMXBean.getTotalStartedThreadCount();
        threadsSnapshot.daemonThreadCount = daemonThreadCount;
        threadsSnapshot.currentThreadCount = currentThreadCount;
        threadsSnapshot.peakThreadCount = peakThreadCount;
        threadsSnapshot.totalStartedThreadCount = totalStartedThreadCount;

        final long[] allThreadIds = threadMXBean.getAllThreadIds();
        final long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
        final long[] monitorDeadlockedThreadIds = threadMXBean.findMonitorDeadlockedThreads();
        threadsSnapshot.deadlockedThreadIds = deadlockedThreadIds;
        threadsSnapshot.monitorDeadlockedThreadIds = monitorDeadlockedThreadIds;

        // stick with threadId to correlate interesting threadInfo data with thread's stackTrace
        final Map<Long, StackTraceElement[]> allThreadIdStackTraces = new HashMap<>();
        final Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
        for (final Map.Entry<Thread, StackTraceElement[]> threadStackEntry : allStackTraces
            .entrySet()) {
          allThreadIdStackTraces.put(threadStackEntry.getKey().getId(),
              threadStackEntry.getValue());
        }

        for (final ThreadInfo threadInfo : threadMXBean.getThreadInfo(allThreadIds)) {
          if (threadInfo != null) {
            final State threadState = threadInfo.getThreadState();
            if (statesOfInterest.contains(threadState)) {
              // switch (threadState) {
              // case NEW:
              // case RUNNABLE:
              // case BLOCKED:
              // case WAITING:
              // case TIMED_WAITING:
              // case TERMINATED:
              // break;
              final Long threadId = threadInfo.getThreadId();
              final String threadName = threadInfo.getThreadName();
              final String lockName = threadInfo.getLockName();
              final String lockOwnerName = threadInfo.getLockOwnerName();
              final long threadBlockedMillis = threadInfo.getBlockedTime();
              final StackTraceElement[] threadStack = allThreadIdStackTraces.get(threadId);
              final ThreadSnapshot threadSnapshot = new ThreadSnapshot();
              threadSnapshot.threadId = threadId;
              threadSnapshot.threadName = threadName;
              threadSnapshot.threadState = threadState;
              threadSnapshot.lockName = lockName;
              threadSnapshot.lockOwnerName = lockOwnerName;
              threadSnapshot.threadBlockedMillis = threadBlockedMillis;
              threadSnapshot.threadStack = threadStack;
              threadsSnapshot.threadSnapshotContainer.add(threadSnapshot);
            }
          }
        }
        this.threadsSnapshot.set(threadsSnapshot);
        logger.info(threadsSnapshot);
        sleep(probeMillis);
      }
    } catch (InterruptedException problem) {
      // exit run()
    }
    logger.info("Doused probe");
  }

  public ThreadsSnapshot getThreadsSnapshot() {
    return threadsSnapshot.get();
  }

  public static final class ThreadsSnapshot {
    public int daemonThreadCount;
    public int currentThreadCount;
    public int peakThreadCount;
    public long totalStartedThreadCount;
    public long[] deadlockedThreadIds;
    public long[] monitorDeadlockedThreadIds;
    public final List<ThreadSnapshot> threadSnapshotContainer = new ArrayList<>();

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("ThreadsSnapshot [daemonThreads=").append(daemonThreadCount)
          .append(", currentThreads=").append(currentThreadCount).append(", peakThreads=")
          .append(peakThreadCount).append(", totalStartedThreads=").append(totalStartedThreadCount)
          .append(", deadlockedThreadIds=").append(deadlockedThreadIds)
          .append(", monitorDeadlockedThreadIds=").append(monitorDeadlockedThreadIds).append(", ")
          .append(threadSnapshotContainer).append("]");
      return builder.toString();
    }
  }

  public static final class ThreadSnapshot {
    public Long threadId;
    public String threadName;
    public State threadState;
    public String lockName;
    public String lockOwnerName;
    public long threadBlockedMillis;
    public StackTraceElement[] threadStack;

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("\nThreadSnapshot [id=").append(threadId).append(", name=").append(threadName)
          .append(", state=").append(threadState).append(", lock=").append(lockName)
          .append(", lockOwner=").append(lockOwnerName).append(", blockedMillis=")
          .append(threadBlockedMillis).append(stringifyStack(threadStack)).append("]");
      return builder.toString();
    }

    private static String stringifyStack(final StackTraceElement[] stackTrace) {
      final StringBuilder builder = new StringBuilder();
      for (final StackTraceElement element : stackTrace) {
        builder.append("\n    ").append(element.toString());
      }
      return builder.toString();
    }
  }

}
