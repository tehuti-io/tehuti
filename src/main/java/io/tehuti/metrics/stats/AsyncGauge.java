package io.tehuti.metrics.stats;

import io.tehuti.metrics.Measurable;
import io.tehuti.metrics.MetricConfig;
import io.tehuti.metrics.NamedMeasurableStat;
import io.tehuti.metrics.SimpleMeasurable;
import io.tehuti.utils.RedundantLogFilter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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

  private final ExecutorService metricValueMeasurementThreadPool;
  private final String metricName;
  private double cachedMeasurement = 0.0;
  private long lastMeasurementStartTimeInMs = System.currentTimeMillis();
  private CompletableFuture<Double> lastMeasurementFuture = null;

  private final Measurable measurable;

  public AsyncGauge(Measurable measurable, ExecutorService metricValueMeasurementThreadPool, String metricName) {
    this.measurable = measurable;
    this.metricValueMeasurementThreadPool = metricValueMeasurementThreadPool;
    this.metricName = metricName;
  }

  public AsyncGauge(SimpleMeasurable measurable, ExecutorService metricValueMeasurementThreadPool, String metricName) {
    this.measurable = measurable;
    this.metricValueMeasurementThreadPool = metricValueMeasurementThreadPool;
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
    // If the thread pool is shutdown, return the cached value
    if (metricValueMeasurementThreadPool.isShutdown()) {
      return cachedMeasurement;
    }
    // If the last measurement future exists, meaning the last measurement didn't finish fast enough. In this case:
    // 1. If the last measurement future is done, update the cached value, log which metric measurement is slow.
    // 2. If the last measurement future is still running, cancel it to prevent OutOfMemory issue, and log.
    if (lastMeasurementFuture != null) {
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
        } catch (InterruptedException e) {
          throw new RuntimeException("Metric measurement is interrupted for metric " + metricName, e);
        }
      } else {
        lastMeasurementFuture.cancel(true);
        String warningMessagePrefix = String.format(
            "The last measurement for metric %s is still running. " + "Cancel it to prevent OutOfMemory issue.",
            metricName);
        if (!REDUNDANT_LOG_FILTER.isRedundantLog(warningMessagePrefix)) {
          LOGGER.warn(String.format("%s Return the cached value: %f", warningMessagePrefix, cachedMeasurement));
        }
      }
    }

    // Submit a new measurement task for the current minute
    lastMeasurementStartTimeInMs = System.currentTimeMillis();
    lastMeasurementFuture =
        CompletableFuture.supplyAsync(() -> this.measurable.measure(config, now), metricValueMeasurementThreadPool);
    // Try to wait for the CompletableFuture for up to 500ms. If it times out, return the cached value;
    // otherwise, update the cached value and return the latest result.
    try {
      cachedMeasurement = lastMeasurementFuture.get(500, TimeUnit.MILLISECONDS);
      lastMeasurementFuture = null;
      return cachedMeasurement;
    } catch (TimeoutException e) {
      // Do nothing; the cached value will be returned
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
