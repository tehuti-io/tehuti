package io.tehuti.metrics.stats;

import io.tehuti.metrics.AsyncGaugeConfig;
import io.tehuti.metrics.Measurable;
import io.tehuti.metrics.MetricConfig;
import io.tehuti.metrics.NamedMeasurableStat;
import io.tehuti.utils.RedundantLogFilter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


/**
 * A gauge metric type that will measure the value for at most 500ms; if the measurement takes longer than 500ms, it will
 * return the cached value. This is useful for metrics that are expensive to measure.
 */
public class AsyncGauge implements NamedMeasurableStat {
  private static final Logger LOGGER = LogManager.getLogger(AsyncGauge.class);
  private static final RedundantLogFilter REDUNDANT_LOG_FILTER = RedundantLogFilter.getRedundantLogFilter();
  private final String metricName;
  private double cachedMeasurement = 0.0;
  private long lastMeasurementStartTimeInMs = System.currentTimeMillis();
  private CompletableFuture<Double> lastMeasurementFuture = null;

  private final Measurable measurable;

  public static final AsyncGaugeConfig DEFAULT_ASYNC_GAUGE_CONFIG =
      new AsyncGaugeConfig(Executors.newFixedThreadPool(10, r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true); // Set the thread as daemon
        return thread;
      }), TimeUnit.MINUTES.toMillis(1), 500);

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
    AsyncGaugeConfig asyncGaugeConfig = config.getAsyncGaugeConfig();
    if (asyncGaugeConfig == null) {
      asyncGaugeConfig = DEFAULT_ASYNC_GAUGE_CONFIG;
    }
    // If the thread pool is shutdown, return the cached value
    if (asyncGaugeConfig.getMetricsMeasurementExecutor().isShutdown()) {
      return cachedMeasurement;
    }

    if (lastMeasurementFuture == null) {
      // First time running measurement; or previous measurement finished fast enough
      return submitNewMeasurementTask(config, now, asyncGaugeConfig);
    } else {
      // If the last measurement future exists, meaning the last measurement didn't finish fast enough. In this case:
      // 1. If the last measurement future is done, update the cached value, log which metric measurement is slow.
      // 2. If the last measurement future is still running, cancel it to prevent OutOfMemory issue, and log.
      if (lastMeasurementFuture.isDone()) {
        try {
          cachedMeasurement = lastMeasurementFuture.get();
          long measurementTimeInMs = System.currentTimeMillis() - lastMeasurementStartTimeInMs;
          String warningMessagePrefix = String.format("The measurement for metric %s", metricName);
          if (!REDUNDANT_LOG_FILTER.isRedundantLog(warningMessagePrefix)) {
            LOGGER.warn(String.format("%s took %d ms; the metric value is %f", warningMessagePrefix, measurementTimeInMs,
                cachedMeasurement));
          }
        } catch (ExecutionException e) {
          String errorMessage = String.format("Failed to get a done measurement future for metric %s. ", metricName);
          if (!REDUNDANT_LOG_FILTER.isRedundantLog(errorMessage)) {
            LOGGER.error(errorMessage, e);
          }
          // Update the cached value to a negative value to indicate the measurement failure
          cachedMeasurement = -1.0;
        } catch (InterruptedException e) {
          throw new RuntimeException("Metric measurement is interrupted for metric " + metricName, e);
        }
        // Always try to get the freshest measurement value
        // Reason: let's say the initial wait time is 500ms, and the previous measurement took 600ms. In this case, if we
        //         return the previous measurement value, it would be 59 seconds stale, assuming the measurement interval is 1 minute.
        return submitNewMeasurementTask(config, now, asyncGaugeConfig);
      } else {
        // If the last measurement future is still running but hasn't exceeded the max timeout, keep it running and return the cached value.
        // Otherwise, cancel the last measurement future to prevent OutOfMemory issue, and submit a new measurement task.
        if (System.currentTimeMillis() - lastMeasurementStartTimeInMs < asyncGaugeConfig.getMaxMetricsMeasurementTimeoutInMs()) {
          return cachedMeasurement;
        } else {
          lastMeasurementFuture.cancel(true);
          String warningMessagePrefix = String.format(
              "The last measurement for metric %s is still running. " + "Cancel it to prevent OutOfMemory issue.",
              metricName);
          if (!REDUNDANT_LOG_FILTER.isRedundantLog(warningMessagePrefix)) {
            LOGGER.warn(String.format("%s Return the cached value: %f", warningMessagePrefix, cachedMeasurement));
          }
          return submitNewMeasurementTask(config, now, asyncGaugeConfig);
        }
      }
    }
  }

  private double submitNewMeasurementTask(MetricConfig config, long now, AsyncGaugeConfig asyncGaugeConfig) {
    try {
      // Submit a new measurement task for the current minute
      lastMeasurementStartTimeInMs = System.currentTimeMillis();
      lastMeasurementFuture =
          CompletableFuture.supplyAsync(() -> this.measurable.measure(config, now), asyncGaugeConfig.getMetricsMeasurementExecutor());
      /**
       * Try to wait for the CompletableFuture for {@link AsyncGaugeConfig#initialMetricsMeasurementTimeoutInMs}.
       * If it times out, return the cached value; otherwise, update the cached value and return the latest result.
       */
      cachedMeasurement = lastMeasurementFuture.get(asyncGaugeConfig.getInitialMetricsMeasurementTimeoutInMs(), TimeUnit.MILLISECONDS);
      lastMeasurementFuture = null;
      return cachedMeasurement;
    } catch (RejectedExecutionException | TimeoutException e) {
      // If the thread pool is shutdown or the measurement takes longer than 500ms, return the cached value
      return cachedMeasurement;
    } catch (ExecutionException e) {
      String errorMessage = String.format("Error when measuring value for metric %s.", metricName);
      if (!REDUNDANT_LOG_FILTER.isRedundantLog(errorMessage)) {
        LOGGER.error(errorMessage, e);
      }
      return cachedMeasurement;
    } catch (InterruptedException e) {
      throw new RuntimeException("Metric measurement is interrupted for metric " + metricName, e);
    }
  }
}
