package com.github.jaemons;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A daemon that continually probes a JVM's direct-memory usage.
 * 
 * It also provides some dangerous ways to purge off-heap buffer's usage of direct-memory.
 * 
 * @author gaurav
 */
public final class DirectMemoryProbe extends Thread {
  private static final Logger logger =
      LogManager.getLogger(DirectMemoryProbe.class.getSimpleName());
  private final AtomicReference<DirectMemorySnapshot> directMemorySnapshot =
      new AtomicReference<>();
  private final long probeMillis;

  public DirectMemoryProbe(final long probeMillis) {
    this.probeMillis = probeMillis;
    setDaemon(true);
    setName("direct-memory-probe");
    start();
  }

  @Override
  public void run() {
    logger.info("Initialized probe");
    try {
      while (!isInterrupted()) {
        final DirectMemorySnapshot directMemorySnapshot = new DirectMemorySnapshot();
        directMemorySnapshot.probeTime = System.currentTimeMillis();
        final MemoryUsage offHeapUsage =
            ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        directMemorySnapshot.initMemory = offHeapUsage.getInit();
        directMemorySnapshot.maxMemory = offHeapUsage.getMax();
        directMemorySnapshot.usedMemory = offHeapUsage.getUsed();
        directMemorySnapshot.committedMemory = offHeapUsage.getCommitted();

        final List<BufferPoolMXBean> pools =
            ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        for (final BufferPoolMXBean pool : pools) {
          final String name = pool.getName();
          final long memoryUsed = pool.getMemoryUsed();
          final long capacityEstimate = pool.getTotalCapacity();
          final long bufferCount = pool.getCount();

          final BufferSnapshot snapshot = new BufferSnapshot();
          snapshot.poolName = name;
          snapshot.memoryUsed = memoryUsed;
          snapshot.capacityEstimate = capacityEstimate;
          snapshot.bufferCount = bufferCount;
          directMemorySnapshot.bufferSnapshots.add(snapshot);
        }
        this.directMemorySnapshot.set(directMemorySnapshot);
        logger.info(directMemorySnapshot);
        sleep(probeMillis);
      }
    } catch (InterruptedException problem) {
      // exit run()
    }
    logger.info("Doused probe");
  }

  public DirectMemorySnapshot getDirectMemoryUsage() {
    return directMemorySnapshot.get();
  }

  // Warning: handle with care
  public static void gcOffHeapBuffer(final ByteBuffer buffer) throws Exception {
    logger.info("Garbage collecting {}", buffer);
    final Method cleanerMethod = buffer.getClass().getMethod("cleaner");
    cleanerMethod.setAccessible(true);
    final Object cleaner = cleanerMethod.invoke(buffer);
    final Method cleanMethod = cleaner.getClass().getMethod("clean");
    cleanMethod.setAccessible(true);
    cleanMethod.invoke(cleaner);
  }

  public final static class DirectMemorySnapshot {
    public final List<BufferSnapshot> bufferSnapshots = new ArrayList<>();
    public long initMemory;
    public long maxMemory;
    public long usedMemory;
    public long committedMemory;
    public long probeTime;

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("DirectMemorySnapshot [").append(bufferSnapshots).append(", initMemory=")
          .append(initMemory).append(", maxMemory=").append(maxMemory).append(", usedMemory=")
          .append(usedMemory).append(", committedMemory=").append(committedMemory)
          .append(", probeTime=").append(probeTime).append("]");
      return builder.toString();
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + (int) (probeTime ^ (probeTime >>> 32));
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof DirectMemorySnapshot)) {
        return false;
      }
      DirectMemorySnapshot other = (DirectMemorySnapshot) obj;
      if (probeTime != other.probeTime) {
        return false;
      }
      return true;
    }
  }

  /**
   * A snapshot of direct memory buffer.
   */
  public final static class BufferSnapshot {
    public String poolName;
    public long memoryUsed;
    public long capacityEstimate;
    public long bufferCount;

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("BufferSnapshot [poolName=").append(poolName).append(", memoryUsed=")
          .append(memoryUsed).append(", capacityEstimate=").append(capacityEstimate)
          .append(", bufferCount=").append(bufferCount).append("]");
      return builder.toString();
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((poolName == null) ? 0 : poolName.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (!(obj instanceof BufferSnapshot)) {
        return false;
      }
      BufferSnapshot other = (BufferSnapshot) obj;
      if (poolName == null) {
        if (other.poolName != null) {
          return false;
        }
      } else if (!poolName.equals(other.poolName)) {
        return false;
      }
      return true;
    }
  }

}
