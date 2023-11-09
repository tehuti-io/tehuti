package io.tehuti.utils;

import java.util.BitSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RedundantLogFilter {
  public static final int DEFAULT_BITSET_SIZE = 8 * 1024 * 1024 * 16; // 16MB
  public static final long DEFAULT_NO_REDUNDANT_LOG_DURATION_MS = TimeUnit.MINUTES.toMillis(10); // 10 minute

  private static RedundantLogFilter singleton;

  private final int bitSetSize;
  private final ScheduledExecutorService cleanerExecutor = Executors.newScheduledThreadPool(1);

  private BitSet activeBitset;
  private BitSet oldBitSet;

  public RedundantLogFilter() {
    this(DEFAULT_BITSET_SIZE, DEFAULT_NO_REDUNDANT_LOG_DURATION_MS);
  }

  public RedundantLogFilter(int bitSetSize, long noRedundantLogDurationMs) {
    this.bitSetSize = bitSetSize;
    activeBitset = new BitSet(bitSetSize);
    oldBitSet = new BitSet(bitSetSize);
    cleanerExecutor.scheduleAtFixedRate(
        this::clearBitSet,
        noRedundantLogDurationMs,
        noRedundantLogDurationMs,
        TimeUnit.MILLISECONDS);
  }

  public synchronized static RedundantLogFilter getRedundantLogFilter() {
    if (singleton == null) {
      singleton = new RedundantLogFilter(DEFAULT_BITSET_SIZE, DEFAULT_NO_REDUNDANT_LOG_DURATION_MS);
    }
    return singleton;
  }

  public boolean isRedundantLog(String logMessage) {
    return isRedundantLog(logMessage, true);
  }

  public boolean isRedundantLog(String logMessage, boolean updateRedundancy) {
    if (logMessage == null) {
      return true;
    }
    int index = getIndex(logMessage);
    return isRedundant(index, updateRedundancy);
  }

  public final void clearBitSet() {
    synchronized (this) {
      // Swap bit sets so we are not blocked by clear operation.
      BitSet temp = oldBitSet;
      oldBitSet = activeBitset;
      activeBitset = temp;
    }
    oldBitSet.clear();
  }

  public void shutdown() {
    cleanerExecutor.shutdownNow();
  }

  protected int getIndex(String key) {
    return Math.abs((key).hashCode() % bitSetSize);
  }

  protected boolean isRedundant(int index, boolean updateRedundancy) {
    if (!activeBitset.get(index)) {
      // It's possible that we found the bit was not set, then activeBitset is changed, and we set the bit in the new
      // set. But it doesn't matter.
      if (updateRedundancy) {
        activeBitset.set(index);
      }
      return false;
    }
    return true;
  }
}
