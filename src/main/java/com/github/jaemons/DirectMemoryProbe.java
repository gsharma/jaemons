package com.github.jaemons;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
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
  private final AtomicReference<List<DirectMemorySnapshot>> directMemorySnapshots =
      new AtomicReference<>();
  private final long probeMillis;

  public DirectMemoryProbe(final long probeMillis) {
    this.probeMillis = probeMillis;
    ManagementFactory.getMemoryMXBean().setVerbose(true);
    setDaemon(true);
    setName("direct-memory-probe");
    start();
  }

  @Override
  public void run() {
    logger.info("Initialized probe");
    try {
      while (!isInterrupted()) {
        final List<DirectMemorySnapshot> directMemorySnapshots = new ArrayList<>();
        final List<MemoryPoolMXBean> memoryPoolBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (final MemoryPoolMXBean memoryPoolBean : memoryPoolBeans) {
          if (memoryPoolBean != null && memoryPoolBean.getType() == MemoryType.NON_HEAP) {
            final DirectMemorySnapshot directMemorySnapshot = new DirectMemorySnapshot();
            directMemorySnapshot.probeTime = System.currentTimeMillis();
            directMemorySnapshot.poolName = memoryPoolBean.getName();
            final MemoryUsage offHeapUsage = memoryPoolBean.getUsage();
            directMemorySnapshot.initMemory = offHeapUsage.getInit();
            directMemorySnapshot.maxMemory = offHeapUsage.getMax();
            directMemorySnapshot.usedMemory = offHeapUsage.getUsed();
            directMemorySnapshot.committedMemory = offHeapUsage.getCommitted();
            directMemorySnapshots.add(directMemorySnapshot);
            // logger.info(Arrays.deepToString(memoryPoolBean.getMemoryManagerNames()));
            logger.info(directMemorySnapshot);
          }
          // TODO: populate more snapshots: eg. heap usage, if needed
          // logger.info("{}:{}:{}", memoryPoolBean.getType(), memoryPoolBean.getName(),
          // memoryPoolBean.getUsage());
        }
        final DirectMemorySnapshot directMemorySnapshot = new DirectMemorySnapshot();
        directMemorySnapshot.probeTime = System.currentTimeMillis();
        directMemorySnapshot.poolName = DirectMemoryPool.DIRECT_MAPPED.getPoolName();
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
        logger.info(directMemorySnapshot);
        directMemorySnapshots.add(directMemorySnapshot);
        this.directMemorySnapshots.set(directMemorySnapshots);
        sleep(probeMillis);
      }
    } catch (InterruptedException problem) {
      // exit run()
    }
    logger.info("Doused probe");
  }

  public enum DirectMemoryPool {
    CODE_CACHE("Code Cache"), METASPACE("Metaspace"), COMPRESSED_CLASS_SPACE(
        "Compressed Class Space"), DIRECT_MAPPED("Direct,Mapped");

    private String poolName;

    private DirectMemoryPool(final String poolName) {
      this.poolName = poolName;
    }

    public String getPoolName() {
      return poolName;
    }

    public static List<String> getAllPoolNames() {
      final List<String> allPoolNames = new ArrayList<>();
      for (final DirectMemoryPool pool : values()) {
        allPoolNames.add(pool.poolName);
      }
      return allPoolNames;
    }
  }

  public List<DirectMemorySnapshot> getDirectMemoryUsage() {
    return directMemorySnapshots.get();
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
    public String poolName;
    public final List<BufferSnapshot> bufferSnapshots = new ArrayList<>();
    public long initMemory;
    public long maxMemory;
    public long usedMemory;
    public long committedMemory;
    public long probeTime;

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("DirectMemorySnapshot [poolName=").append(poolName).append(", ")
          .append(bufferSnapshots).append(", initMemory=").append(initMemory).append(", maxMemory=")
          .append(maxMemory).append(", usedMemory=").append(usedMemory).append(", committedMemory=")
          .append(committedMemory).append(", probeTime=").append(probeTime).append("]");
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
