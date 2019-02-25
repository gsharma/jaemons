package com.github.jaemons;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import com.github.jaemons.DirectMemoryProbe.DirectMemorySnapshot;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;

/**
 * Tests to maintain functional sanity of DirectMemoryProbe.
 * 
 * @author gaurav
 */
public class DirectMemoryProbeTest {

  @Test
  public void testSnapshots() throws Exception {
    long probeFrequency = 10L;
    final DirectMemoryProbe probe = new DirectMemoryProbe(probeFrequency);
    // wait for probe to wake-up and collect data
    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(probeFrequency * 4));

    // step 1: query probe for latest snapshot of direct memory usage
    Collection<DirectMemorySnapshot> snapshots = probe.getDirectMemoryUsage();
    assertEquals(2, snapshots.size());

    // expect no direct memory usage
    for (final DirectMemorySnapshot snapshot : snapshots) {
      if (snapshot.poolName.equals("direct")) {
        assertEquals(0L, snapshot.bufferCount);
        assertEquals(0L, snapshot.memoryUsed);
        assertEquals(0L, snapshot.capacityEstimate);
      }

      else if (snapshot.poolName.equals("mapped")) {
        assertEquals(0L, snapshot.bufferCount);
        assertEquals(0L, snapshot.memoryUsed);
        assertEquals(0L, snapshot.capacityEstimate);
      }
    }

    // step 2: alloc direct and mapped memory and collect data from probe
    int directBufferCapacity = 4;
    final ByteBuffer directBuffer = ByteBuffer.allocateDirect(directBufferCapacity);
    int mappedBufferCapacity = 5;
    MappedByteBuffer mappedBuffer = null;
    final File file = new File("/tmp/mapped.txt");
    final RandomAccessFile randomFile = new RandomAccessFile(file, "rw");
    try {
      mappedBuffer =
          randomFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, mappedBufferCapacity);
      for (int iter = 0; iter < mappedBufferCapacity; iter++) {
        mappedBuffer.put((byte) 'z');
      }
    } finally {
      randomFile.close();
      file.delete();
    }

    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(probeFrequency * 4));
    snapshots = probe.getDirectMemoryUsage();
    assertEquals(2, snapshots.size());

    // expect probe to find direct memory usage
    for (final DirectMemorySnapshot snapshot : snapshots) {
      if (snapshot.poolName.equals("direct")) {
        assertEquals(1L, snapshot.bufferCount);
        assertEquals(directBufferCapacity, snapshot.memoryUsed);
        assertEquals(directBufferCapacity, snapshot.capacityEstimate);
      }

      else if (snapshot.poolName.equals("mapped")) {
        assertEquals(1L, snapshot.bufferCount);
        assertEquals(mappedBufferCapacity, snapshot.memoryUsed);
        assertEquals(mappedBufferCapacity, snapshot.capacityEstimate);
      }
    }

    // step 3: garbage collect allocated direct buffer, probe should reflect zero usage
    DirectMemoryProbe.gcOffHeapBuffer(directBuffer);
    DirectMemoryProbe.gcOffHeapBuffer(mappedBuffer);

    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(probeFrequency * 4));
    snapshots = probe.getDirectMemoryUsage();
    assertEquals(2, snapshots.size());

    // expect no direct memory usage since buffers were both garbage-collected
    for (final DirectMemorySnapshot snapshot : snapshots) {
      if (snapshot.poolName.equals("direct")) {
        assertEquals(0L, snapshot.bufferCount);
        assertEquals(0L, snapshot.memoryUsed);
        assertEquals(0L, snapshot.capacityEstimate);
      }

      else if (snapshot.poolName.equals("mapped")) {
        assertEquals(0L, snapshot.bufferCount);
        assertEquals(0L, snapshot.memoryUsed);
        assertEquals(0L, snapshot.capacityEstimate);
      }
    }

    /*
    // step 4: allocate netty bytebuf
    directBufferCapacity = 8;
    final ByteBuf nettyByteBuf =
        new PooledByteBufAllocator(true).directBuffer(directBufferCapacity);
    assertEquals("PooledUnsafeDirectByteBuf", nettyByteBuf.getClass().getSimpleName());
    try {
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(probeFrequency * 2));
      snapshots = probe.getDirectMemoryUsage();
      assertEquals(2, snapshots.size());

      // expect probe to find direct memory usage
      for (final DirectMemorySnapshot snapshot : snapshots) {
        if (snapshot.poolName.equals("direct")) {
          assertEquals(2L, snapshot.bufferCount);
          assertEquals(2, snapshot.memoryUsed);
          assertEquals(1, snapshot.capacityEstimate);
        }

        else if (snapshot.poolName.equals("mapped")) {
          assertEquals(0L, snapshot.bufferCount);
          assertEquals(0L, snapshot.memoryUsed);
          assertEquals(0L, snapshot.capacityEstimate);
        }
      }

      // step 5: release netty direct buffer, probe should reflect zero usage
    } finally {
      nettyByteBuf.release();
    }
    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(probeFrequency * 2));
    snapshots = probe.getDirectMemoryUsage();
    assertEquals(2, snapshots.size());

    // expect no direct memory usage since buffers were both garbage-collected
    for (final DirectMemorySnapshot snapshot : snapshots) {
      if (snapshot.poolName.equals("direct")) {
        assertEquals(0L, snapshot.bufferCount);
        assertEquals(0L, snapshot.memoryUsed);
        assertEquals(0L, snapshot.capacityEstimate);
      }

      else if (snapshot.poolName.equals("mapped")) {
        assertEquals(0L, snapshot.bufferCount);
        assertEquals(0L, snapshot.memoryUsed);
        assertEquals(0L, snapshot.capacityEstimate);
      }
    }
    */

    // step n: interrupt probe
    probe.interrupt();
  }

}
