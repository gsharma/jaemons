package com.github.jaemons;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import com.github.jaemons.ThreadProbe.ThreadsSnapshot;

/**
 * Tests to maintain functional sanity of ThreadProbe.
 * 
 * @author gaurav
 */
public class ThreadProbeTest {

  @Test
  public void testSnapshots() {
    // case 1: setup probe to find all threads
    long probeFrequency = 10L;
    ThreadProbe probe = new ThreadProbe(probeFrequency, null);
    // wait for probe to wake-up and collect data
    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(probeFrequency * 4));

    ThreadsSnapshot snapshot = probe.getThreadsSnapshot();
    assertTrue(snapshot.currentThreadCount > 0);
    assertTrue(snapshot.daemonThreadCount > 0);
    assertTrue(snapshot.peakThreadCount > 0);
    assertTrue(snapshot.totalStartedThreadCount > 0);
    assertFalse(snapshot.threadSnapshotContainer.isEmpty());
    assertNull(snapshot.deadlockedThreadIds);
    assertNull(snapshot.monitorDeadlockedThreadIds);

    // now interrupt probe
    probe.interrupt();

    // case 2: setup probe to find any blocked threads
    probeFrequency = 10L;
    probe =
        new ThreadProbe(probeFrequency, Arrays.asList(new Thread.State[] {Thread.State.BLOCKED}));
    // wait for probe to wake-up and collect data
    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(probeFrequency * 4));

    snapshot = probe.getThreadsSnapshot();
    assertTrue(snapshot.currentThreadCount > 0);
    assertTrue(snapshot.daemonThreadCount > 0);
    assertTrue(snapshot.peakThreadCount > 0);
    assertTrue(snapshot.totalStartedThreadCount > 0);
    // should find no blocked threads
    assertTrue(snapshot.threadSnapshotContainer.isEmpty());
    assertNull(snapshot.deadlockedThreadIds);
    assertNull(snapshot.monitorDeadlockedThreadIds);

    // interrupt probe
    probe.interrupt();
  }

}
