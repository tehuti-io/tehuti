package io.tehuti.metrics;

import io.tehuti.utils.Utils;
import java.util.concurrent.ExecutorService;

/**
 * Configuration for AsyncGauge
 */
public class AsyncGaugeConfig {
  // Thread pool for metrics measurement; ideally these threads should be daemon threads
  private final ExecutorService metricsMeasurementExecutor;
  // The max time to wait for metrics measurement to complete
  // If this limit is exceeded, the measurement task in the thread pool will be cancelled
  private final long maxMetricsMeasurementTimeoutInMs;
  // After the metrics measurement task is submitted to the thread pool, it will try to wait for this amount of time;
  // if this limit is exceeded, AsyncGauge will return the cached value
  private final long initialMetricsMeasurementTimeoutInMs;

  public AsyncGaugeConfig(ExecutorService metricsMeasurementExecutor,
                          long maxMetricsMeasurementTimeoutInMs,
                          long initialMetricsMeasurementTimeoutInMs) {
    Utils.notNull(metricsMeasurementExecutor);
    if (maxMetricsMeasurementTimeoutInMs <= 0) {
      throw new IllegalArgumentException("maxMetricsMeasurementTimeoutInMs must be positive");
    }
    if (initialMetricsMeasurementTimeoutInMs <= 0) {
      throw new IllegalArgumentException("initialMetricsMeasurementTimeoutInMs must be positive");
    }
    this.metricsMeasurementExecutor = metricsMeasurementExecutor;
    this.maxMetricsMeasurementTimeoutInMs = maxMetricsMeasurementTimeoutInMs;
    this.initialMetricsMeasurementTimeoutInMs = initialMetricsMeasurementTimeoutInMs;
  }

  public ExecutorService getMetricsMeasurementExecutor() {
    return metricsMeasurementExecutor;
  }

  public long getMaxMetricsMeasurementTimeoutInMs() {
    return maxMetricsMeasurementTimeoutInMs;
  }

  public long getInitialMetricsMeasurementTimeoutInMs() {
    return initialMetricsMeasurementTimeoutInMs;
  }
}
