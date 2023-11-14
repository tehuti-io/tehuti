package io.tehuti.metrics;

import io.tehuti.utils.Utils;
import java.util.concurrent.ExecutorService;

/**
 * Configuration for AsyncGauge
 */
public class AsyncGaugeConfig {
  private static final long DEFAULT_MAX_TIMEOUT_ERROR_CODE = -1L;

  // Thread pool for metrics measurement; ideally these threads should be daemon threads
  private final ExecutorService metricsMeasurementExecutor;
  // The max time to wait for metrics measurement to complete
  // If this limit is exceeded, the measurement task in the thread pool will be cancelled
  private final long maxMetricsMeasurementTimeoutInMs;
  // After the metrics measurement task is submitted to the thread pool, it will try to wait for this amount of time;
  // if this limit is exceeded, AsyncGauge will return the cached value
  private final long initialMetricsMeasurementTimeoutInMs;

  // The error code returned by the measurement task when it is cancelled due to the max timeout limit
  private final long maxTimeoutErrorCode;

  public AsyncGaugeConfig(ExecutorService metricsMeasurementExecutor,
                          long maxMetricsMeasurementTimeoutInMs,
                          long initialMetricsMeasurementTimeoutInMs) {
    this(metricsMeasurementExecutor, maxMetricsMeasurementTimeoutInMs, initialMetricsMeasurementTimeoutInMs, DEFAULT_MAX_TIMEOUT_ERROR_CODE);
  }

  public AsyncGaugeConfig(ExecutorService metricsMeasurementExecutor,
                          long maxMetricsMeasurementTimeoutInMs,
                          long initialMetricsMeasurementTimeoutInMs,
                          long maxTimeoutErrorCode) {
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
    this.maxTimeoutErrorCode = maxTimeoutErrorCode;
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

  public long getMaxTimeoutErrorCode() {
    return maxTimeoutErrorCode;
  }
}
