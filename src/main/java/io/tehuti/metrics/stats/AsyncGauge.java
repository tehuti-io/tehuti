package io.tehuti.metrics.stats;

import io.tehuti.metrics.AsyncGaugeConfig;
import io.tehuti.metrics.Measurable;
import io.tehuti.metrics.MetricConfig;
import io.tehuti.metrics.NamedMeasurableStat;
import io.tehuti.utils.DaemonThreadFactory;
import io.tehuti.utils.RedundantLogFilter;
import io.tehuti.utils.SystemTime;
import io.tehuti.utils.Time;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


/**
 * A gauge metric type that will measure the value for at most 500ms; if the measurement takes longer than 500ms, it will
 * return the cached value. This is useful for metrics that are expensive to measure.
 */
public class AsyncGauge implements NamedMeasurableStat {
  private final String metricName;
  private double cachedMeasurement = 0.0;
  private long lastMeasurementStartTimeInMs = -1;
  private long lastMeasurementLatencyInMs = -1;
  private CompletableFuture<Double> lastMeasurementFuture = null;

  private final Measurable measurable;

  public static final AsyncGaugeExecutor DEFAULT_ASYNC_GAUGE_EXECUTOR =
      new AsyncGaugeExecutor.Builder().build();

  public AsyncGauge(Measurable measurable, String metricName) {
    this.measurable = measurable;
    this.metricName = metricName;
  }

  @Override
  public String getStatName() {
    return metricName;
  }

  @Override
  public void record(double value, long now) {
    throw new UnsupportedOperationException("AsyncGauge does not support record(double, long); the invalid usage happened to metric " + metricName);
  }

  /**
   * In AsyncGauge, the actual measurement will be treated as an async task carried out by the thread pool. Once
   * the measurement task is submitted, the method will return the cached value immediately (so this value will be 1 minute stale)
   * @param config The configuration for this metric
   * @param now The POSIX time in milliseconds the measurement is being taken
   * @return
   */
  @Override
  public double measure(MetricConfig config, long now) {
    AsyncGaugeExecutor asyncGaugeExecutor = config.getAsyncGaugeExecutor();
    if (asyncGaugeExecutor == null) {
      asyncGaugeExecutor = DEFAULT_ASYNC_GAUGE_EXECUTOR;
    }
    return asyncGaugeExecutor.measure(this, config, now);
  }

  /**
   * In the high-level, {@link AsyncGaugeExecutor} works as follows:
   * 1. There are two executors and one for regular metrics and the other one is for slow metrics.
   * 2. All the metric evaluations are triggered by the caller.
   * 3. If the actual metric execution time exceeds the configured slow metric threshold, it will be moved to slow metric tracking map,
   *    which indicates the following behaviors:
   *    a. The next metric measurement call will return the cached value immediately.
   *    b. The submitted measurable will be executed asynchronously.
   *    c. If the actual measurement runtime latency becomes lower than the slow metric threshold, it will be moved out
   *       of slow metric tracking map.
   * 4. If the actual metric execution time belows the configured slow metric threshold, the following behaviors will be observed:
   *    a. After submitting the measurable to the regular executor, it will wait up to configured {@link AsyncGaugeExecutor#initialMetricsMeasurementTimeoutInMs}
   *       to collect the latest result.
   *    b. If it can't collect the latest value in step #a, the next call will examine the previous execution to decide
   *       whether it should be put into the slow metric tracking map or not.
   *
   * 5. There is an async thread to clean up inactive metrics from slow metric tracking map to avoid the accumulation of garbage
   *    because of metric deletion.
   */
  public static class AsyncGaugeExecutor implements Closeable {

    public static class Builder {
      private int metricMeasurementThreadCount = 3;
      private int slowMetricMeasurementThreadCount = 7;
      private long initialMetricsMeasurementTimeoutInMs = 500;
      private int slowMetricThresholdMs = 100;
      private long maxMetricsMeasurementTimeoutInMs = TimeUnit.MINUTES.toMillis(1);
      private int maxTimeoutErrorCode = -1;
      private long inactiveSlowMetricCleanupThresholdInMs = TimeUnit.MINUTES.toMillis(30);
      private Time time = new SystemTime();
      private long inactiveSlowMetricCleanupIntervalInMs = TimeUnit.MINUTES.toMillis(5);

      public Builder setMetricMeasurementThreadCount(int metricMeasurementThreadCount) {
        this.metricMeasurementThreadCount = metricMeasurementThreadCount;
        return this;
      }

      public Builder setSlowMetricMeasurementThreadCount(int slowMetricMeasurementThreadCount) {
        this.slowMetricMeasurementThreadCount = slowMetricMeasurementThreadCount;
        return this;
      }

      public Builder setInitialMetricsMeasurementTimeoutInMs(long initialMetricsMeasurementTimeoutInMs) {
        this.initialMetricsMeasurementTimeoutInMs = initialMetricsMeasurementTimeoutInMs;
        return this;
      }

      public Builder setMaxMetricsMeasurementTimeoutInMs(long maxMetricsMeasurementTimeoutInMs) {
        this.maxMetricsMeasurementTimeoutInMs = maxMetricsMeasurementTimeoutInMs;
        return this;
      }

      public Builder setMaxTimeoutErrorCode(int maxTimeoutErrorCode) {
        this.maxTimeoutErrorCode = maxTimeoutErrorCode;
        return this;
      }

      public Builder setSlowMetricThresholdMs(int slowMetricThresholdMs) {
        this.slowMetricThresholdMs = slowMetricThresholdMs;
        return this;
      }

      public Builder setInactiveSlowMetricCleanupThresholdInMs(long inactiveSlowMetricCleanupThresholdInMs) {
        this.inactiveSlowMetricCleanupThresholdInMs = inactiveSlowMetricCleanupThresholdInMs;
        return this;
      }

      public Builder setInactiveSlowMetricCleanupIntervalInMs(long inactiveSlowMetricCleanupIntervalInMs) {
        this.inactiveSlowMetricCleanupIntervalInMs = inactiveSlowMetricCleanupIntervalInMs;
        return this;
      }

      public Builder setTime(Time time) {
        this.time = time;
        return this;
      }

      public AsyncGaugeExecutor build() {
        return new AsyncGaugeExecutor(this);
      }
    }


    private static final RedundantLogFilter REDUNDANT_LOG_FILTER = RedundantLogFilter.getRedundantLogFilter();

    private static final Logger LOGGER = LogManager.getLogger(AsyncGaugeExecutor.class);


    // Thread pool for the execution of regular metrics
    private final ExecutorService metricsMeasurementExecutor;
    // Thread pool for the execution of slow metrics
    private final ExecutorService slowMetricsMeasurementExecutor;
    // The max time to wait for metrics measurement to complete
    // If this limit is exceeded, the measurement task in the thread pool will be cancelled
    private final long maxMetricsMeasurementTimeoutInMs;
    // After the metrics measurement task is submitted to the thread pool, it will try to wait for this amount of time;
    // if this limit is exceeded, AsyncGauge will return the cached value
    private final long initialMetricsMeasurementTimeoutInMs;

    // The error code returned by the measurement task when it is cancelled due to the max timeout limit
    private final int maxTimeoutErrorCode;

    // If the execution time of the metric exceeds the threshold, the metric will be moved to slow metric set
    private final int slowMetricThresholdMs;

    // This is used to decide the threshold to clean up inactive slow metrics
    private final long inactiveSlowMetricCleanupThresholdInMs;

    private final ConcurrentHashMap<AsyncGauge, Long> slowAsyncGaugeAccessMap = new ConcurrentHashMap<>();
    private final Time time;

    private final ScheduledExecutorService inactiveSlowMetricCleanupExecutor =
        Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("Inactive_Slow_AsyncGauge_Cleanup"));

    public AsyncGaugeExecutor(Builder builder) {
      if (builder.metricMeasurementThreadCount <= 0) {
        throw new IllegalArgumentException("metricMeasurementThreadCount must be positive");
      }
      if (builder.slowMetricMeasurementThreadCount <= 0) {
        throw new IllegalArgumentException("slowMetricMeasurementThreadCount must be positive");
      }
      if (builder.maxMetricsMeasurementTimeoutInMs <= 0) {
        throw new IllegalArgumentException("maxMetricsMeasurementTimeoutInMs must be positive");
      }
      if (builder.initialMetricsMeasurementTimeoutInMs <= 0) {
        throw new IllegalArgumentException("initialMetricsMeasurementTimeoutInMs must be positive");
      }
      if (builder.slowMetricThresholdMs <= 0) {
        throw new IllegalArgumentException("slowMetricThresholdMs must be positive");
      }
      if (builder.slowMetricThresholdMs > builder.maxMetricsMeasurementTimeoutInMs) {
        throw new IllegalArgumentException("slowMetricThresholdMs: " + builder.slowMetricThresholdMs +
            " must be smaller than or equal to maxMetricsMeasurementTimeoutInMs: "+ builder.maxMetricsMeasurementTimeoutInMs);
      }
      if (builder.inactiveSlowMetricCleanupThresholdInMs <= 0) {
        throw new IllegalArgumentException("slowMetricCleanupThresholdInMs must be positive");
      }
      if (builder.inactiveSlowMetricCleanupIntervalInMs <= 0) {
        throw new IllegalArgumentException("inactiveSlowMetricCleanupIntervalInMs must be positive");
      }
      this.metricsMeasurementExecutor = new ThreadPoolExecutor(builder.metricMeasurementThreadCount,
          builder.metricMeasurementThreadCount,
          0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<Runnable>(10000), // 10k items to avoid OOM
          new DaemonThreadFactory("Async_Gauge_Executor"));;
      this.slowMetricsMeasurementExecutor = new ThreadPoolExecutor(builder.slowMetricMeasurementThreadCount,
          builder.slowMetricMeasurementThreadCount,
          0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<Runnable>(10000), // 10k items to avoid OOM
          new DaemonThreadFactory("Slow_Async_Gauge_Executor"));;
      this.maxMetricsMeasurementTimeoutInMs = builder.maxMetricsMeasurementTimeoutInMs;
      this.initialMetricsMeasurementTimeoutInMs = builder.initialMetricsMeasurementTimeoutInMs;
      this.maxTimeoutErrorCode = builder.maxTimeoutErrorCode;
      this.slowMetricThresholdMs = builder.slowMetricThresholdMs;
      this.inactiveSlowMetricCleanupThresholdInMs = builder.inactiveSlowMetricCleanupThresholdInMs;
      this.time = builder.time;

      // Schedule the inactive metrics cleanup
      inactiveSlowMetricCleanupExecutor.scheduleWithFixedDelay(
          getInActiveSlowMetricCleanupRunnable(),
          builder.inactiveSlowMetricCleanupIntervalInMs,
          builder.inactiveSlowMetricCleanupIntervalInMs,
          TimeUnit.MILLISECONDS
      );
    }

    public Map<AsyncGauge, Long> getSlowAsyncGaugeAccessMap() {
      return Collections.unmodifiableMap(slowAsyncGaugeAccessMap);
    }

    private Runnable getInActiveSlowMetricCleanupRunnable() {
      return () -> {
        Iterator<Map.Entry<AsyncGauge, Long>> iterator = slowAsyncGaugeAccessMap.entrySet().iterator();
        while (iterator.hasNext()) {
          Map.Entry<AsyncGauge, Long> entry = iterator.next();
          if (time.milliseconds() - entry.getValue() >= inactiveSlowMetricCleanupThresholdInMs) {
            LOGGER.info("Removing inactive slow async gauge metric from slow metric tracking: " + entry.getKey().metricName);
            iterator.remove();
          }
        }
      };
    }

    public synchronized double measure(AsyncGauge asyncGauge, MetricConfig config, long now) {
      boolean isSlowMetric = slowAsyncGaugeAccessMap.containsKey(asyncGauge);

      if (isSlowMetric && slowMetricsMeasurementExecutor.isShutdown()
          || !isSlowMetric && metricsMeasurementExecutor.isShutdown()) {
        return asyncGauge.cachedMeasurement;
      }

      if (asyncGauge.lastMeasurementFuture == null) {
        // First time running measurement; or previous measurement finished fast enough
        return submitNewMeasurementTask(asyncGauge, config, now, isSlowMetric);
      } else {
        // If the last measurement future exists, meaning the last measurement didn't finish fast enough. In this case:
        // 1. If the last measurement future is done, update the cached value, log which metric measurement is slow.
        // 2. If the last measurement future is still running, cancel it to prevent OutOfMemory issue, and log.
        if (asyncGauge.lastMeasurementFuture.isDone()) {
          try {
            /**
             * Get the measurement duration of last execution to see whether we should move the metric to {@link #slowAsyncGaugeSet} or not.
             */
            if (asyncGauge.lastMeasurementLatencyInMs > slowMetricThresholdMs) {
              if (!isSlowMetric) {
                LOGGER.warn(String.format("The measurement for metric %s took %d ms; the metric value is %f, moved this metric to slow metric tracking",
                    asyncGauge.metricName, asyncGauge.lastMeasurementLatencyInMs, asyncGauge.cachedMeasurement));
                isSlowMetric = true;
                slowAsyncGaugeAccessMap.put(asyncGauge, time.milliseconds());
              }
            } else if (isSlowMetric) {
              slowAsyncGaugeAccessMap.remove(asyncGauge);
              isSlowMetric = false;
              LOGGER.info("The measurement for metric: " + asyncGauge.metricName + " took " +
                  asyncGauge.lastMeasurementLatencyInMs + " ms, moved this metric out of slow metric tracking");
            }
            asyncGauge.cachedMeasurement = asyncGauge.lastMeasurementFuture.get();
          } catch (ExecutionException e) {
            String errorMessage = String.format("Failed to get a done measurement future for metric %s. ", asyncGauge.metricName);
            if (!REDUNDANT_LOG_FILTER.isRedundantLog(errorMessage)) {
              LOGGER.error(errorMessage, e);
            }
            // Update the cached value to a negative value to indicate the measurement failure
            asyncGauge.cachedMeasurement = -1.0;
          } catch (InterruptedException e) {
            throw new RuntimeException("Metric measurement is interrupted for metric " + asyncGauge.metricName, e);
          }
          // Always try to get the freshest measurement value
          // Reason: let's say the initial wait time is 500ms, and the previous measurement took 600ms. In this case, if we
          //         return the previous measurement value, it would be 59 seconds stale, assuming the measurement interval is 1 minute.
          return submitNewMeasurementTask(asyncGauge, config, now, isSlowMetric);
        } else {
          // If the last measurement future is still running but hasn't exceeded the max timeout, keep it running and return the cached value.
          // Otherwise, cancel the last measurement future to prevent OutOfMemory issue, and submit a new measurement task.
          if (asyncGauge.lastMeasurementStartTimeInMs < 0 // Measurable hasn't been executed yet
              || time.milliseconds() - asyncGauge.lastMeasurementStartTimeInMs < maxMetricsMeasurementTimeoutInMs) {
            return asyncGauge.cachedMeasurement;
          } else {
            asyncGauge.cachedMeasurement = maxTimeoutErrorCode;
            asyncGauge.lastMeasurementFuture.cancel(true);
            String warningMessagePrefix = String.format(
                "The last measurement for metric %s is still running. " + "Cancel it to prevent OutOfMemory issue.",
                asyncGauge.metricName);
            if (!REDUNDANT_LOG_FILTER.isRedundantLog(warningMessagePrefix)) {
              LOGGER.warn(String.format("%s Return the error code: %f, and put it in slow metric set",
                  warningMessagePrefix, asyncGauge.cachedMeasurement));
            }
            if (!isSlowMetric) {
              slowAsyncGaugeAccessMap.put(asyncGauge, time.milliseconds());
              isSlowMetric = true;
            }
            return submitNewMeasurementTask(asyncGauge, config, now, isSlowMetric);
          }
        }
      }
    }

    private double submitNewMeasurementTask(AsyncGauge asyncGauge, MetricConfig config, long now, boolean isSlowMetric) {
      try {
        // Reset the tracking for the new measurement task
        asyncGauge.lastMeasurementStartTimeInMs = -1;
        asyncGauge.lastMeasurementLatencyInMs = -1;
        asyncGauge.lastMeasurementFuture = null;
        asyncGauge.lastMeasurementFuture = CompletableFuture.supplyAsync(
            () -> {
              try {
                asyncGauge.lastMeasurementStartTimeInMs = time.milliseconds();
                return asyncGauge.measurable.measure(config, now);
              } finally {
                asyncGauge.lastMeasurementLatencyInMs = time.milliseconds() - asyncGauge.lastMeasurementStartTimeInMs;
              }
            },
            isSlowMetric ? slowMetricsMeasurementExecutor : metricsMeasurementExecutor
        );

        if (isSlowMetric) {
          // No wait for slow metrics
          return asyncGauge.cachedMeasurement;
        }
        /**
         * Try to wait for the CompletableFuture for {@link AsyncGaugeConfig#initialMetricsMeasurementTimeoutInMs}.
         * If it times out, return the cached value; otherwise, update the cached value and return the latest result.
         */
        asyncGauge.cachedMeasurement = asyncGauge.lastMeasurementFuture.get(initialMetricsMeasurementTimeoutInMs, TimeUnit.MILLISECONDS);
        asyncGauge.lastMeasurementFuture = null;
        return asyncGauge.cachedMeasurement;
      } catch (RejectedExecutionException e) {
        // The queue is saturated.
        return -1;
      } catch (TimeoutException e) {
        // If the thread pool is shutdown or the measurement takes longer than 500ms, return the cached value
        return asyncGauge.cachedMeasurement;
      } catch (ExecutionException e) {
        String errorMessage = String.format("Error when measuring value for metric %s.", asyncGauge.metricName);
        if (!REDUNDANT_LOG_FILTER.isRedundantLog(errorMessage)) {
          LOGGER.error(errorMessage, e);
        }
        asyncGauge.lastMeasurementFuture = null;
        asyncGauge.cachedMeasurement = -1;
        return asyncGauge.cachedMeasurement;
      } catch (InterruptedException e) {
        throw new RuntimeException("Metric measurement is interrupted for metric " + asyncGauge.metricName, e);
      } finally {
        if (asyncGauge.lastMeasurementFuture == null) {
          /**
           * The latest execution finished and check whether we should put it into slow metric set or not.
           */
          if (asyncGauge.lastMeasurementLatencyInMs > slowMetricThresholdMs && !isSlowMetric) {
            LOGGER.warn("Putting metric: " + asyncGauge.metricName + " into slow metric tracking");
            slowAsyncGaugeAccessMap.put(asyncGauge, time.milliseconds());
          }
        }
      }
    }

    @Override
    public void close() throws IOException {
      this.metricsMeasurementExecutor.shutdownNow();
      this.slowMetricsMeasurementExecutor.shutdownNow();
      this.inactiveSlowMetricCleanupExecutor.shutdownNow();
    }
  }
}
