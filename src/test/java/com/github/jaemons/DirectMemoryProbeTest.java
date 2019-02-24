package com.github.jaemons;

import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import com.github.jaemons.DirectMemoryProbe.DirectMemorySnapshot;

/**
 * Tests to maintain functional sanity of DirectMemoryProbe.
 * 
 * @author gaurav
 */
public class DirectMemoryProbeTest {

  @Test
  public void testSnapshots() {
    long probeFrequency = 10L;
    final DirectMemoryProbe probe = new DirectMemoryProbe(probeFrequency);
    // wait for probe to wake-up and collect data
    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(probeFrequency * 2));
    // query probe for latest snapshot of direct memory usage
    final Collection<DirectMemorySnapshot> snapshots = probe.getDirectMemoryUsage();
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

    // TODO: alloc direct and mapped memory and collect data from probe

    probe.interrupt();
  }

}
