package com.github.jaemons;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A daemon that continually probes a JVM's direct-memory usage.
 * 
 * @author gaurav
 */
public final class DirectMemoryProbe extends Thread {
  private static final Logger logger =
      LogManager.getLogger(DirectMemoryProbe.class.getSimpleName());
  private final ConcurrentMap<String, DirectMemorySnapshot> snapshots = new ConcurrentHashMap<>();
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
        final MemoryUsage offHeapUsage =
            ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        logger.info(offHeapUsage);

        final List<BufferPoolMXBean> pools =
            ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        for (final BufferPoolMXBean pool : pools) {
          String name = pool.getName();
          long memoryUsed = pool.getMemoryUsed();
          long capacityEstimate = pool.getTotalCapacity();
          long bufferCount = pool.getCount();

          final DirectMemorySnapshot snapshot = new DirectMemorySnapshot();
          snapshot.poolName = name;
          snapshot.probeTime = System.currentTimeMillis();
          snapshot.memoryUsed = memoryUsed;
          snapshot.capacityEstimate = capacityEstimate;
          snapshot.bufferCount = bufferCount;
          snapshots.put(name, snapshot);
        }
        logger.info(snapshots);
        sleep(probeMillis);
      }
    } catch (InterruptedException problem) {
      // exit run()
    }
    logger.info("Doused probe");
  }

  public Collection<DirectMemorySnapshot> getDirectMemoryUsage() {
    return snapshots.values();
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

  /**
   * A snapshot of direct memory pool.
   */
  public final static class DirectMemorySnapshot {
    public String poolName;
    public long memoryUsed;
    public long capacityEstimate;
    public long bufferCount;
    public long probeTime;

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("DirectMemorySnapshot [poolName=").append(poolName).append(", memoryUsed=")
          .append(memoryUsed).append(", capacityEstimate=").append(capacityEstimate)
          .append(", bufferCount=").append(bufferCount).append(", probeTime=").append(probeTime)
          .append("]");
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
      if (!(obj instanceof DirectMemorySnapshot)) {
        return false;
      }
      DirectMemorySnapshot other = (DirectMemorySnapshot) obj;
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
