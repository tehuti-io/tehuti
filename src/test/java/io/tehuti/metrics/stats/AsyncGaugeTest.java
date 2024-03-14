package io.tehuti.metrics.stats;

import static org.junit.Assert.assertEquals;

import io.tehuti.metrics.AsyncGaugeConfig;
import io.tehuti.metrics.MetricConfig;
import io.tehuti.utils.MockTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;


public class AsyncGaugeTest {
  @Test
  public void testTimeoutMeasurementReturnsErrorCode() throws InterruptedException {
    // Create a AsyncGauge metric whose measurement will block forever
    AsyncGauge gauge = new AsyncGauge((c, t) -> {
      try {
        // Block forever
        Thread.sleep(Long.MAX_VALUE);
      } catch (InterruptedException e) {
        // Interrupt will unblock the measurement
      }
      return 0.0;
    }, "testMetric");
    // Set AsyncGaugeConfig with max timeout of 100 ms and initial timeout of 10 ms
    AsyncGauge.AsyncGaugeExecutor asyncGaugeExecutor = new AsyncGauge.AsyncGaugeExecutor.Builder()
        .setMaxMetricsMeasurementTimeoutInMs(100)
        .setInitialMetricsMeasurementTimeoutInMs(10)
        .setMetricMeasurementThreadCount(1)
        .setSlowMetricThresholdMs(50)
        .setSlowMetricMeasurementThreadCount(1)
        .build();
    MetricConfig metricConfig = new MetricConfig(asyncGaugeExecutor);
    // The first measurement should return 0 because the measurement task will not complete after the initial timeout
    assertEquals("The first measurement should return 0 because the measurement task will not complete after the initial timeout",
        0.0, gauge.measure(metricConfig, System.currentTimeMillis()), 0.01);
    // Wait for the max timeout
    Thread.sleep(101);
    /**
     * The second measurement should return {@link AsyncGaugeConfig#DEFAULT_MAX_TIMEOUT_ERROR_CODE} because the
     * measurement task will not complete after the max timeout
     */
    assertEquals("The second measurement should return error code",
        AsyncGaugeConfig.DEFAULT_MAX_TIMEOUT_ERROR_CODE, gauge.measure(metricConfig, System.currentTimeMillis()), 0.01);
  }

  @Test(timeout = 10000)
  public void testCallerOfAsyncGaugeWillNeverBeBlocked() {
    // Create a AsyncGauge metric whose measurement will block forever even if it's interrupted
    AsyncGauge gauge = new AsyncGauge((c, t) -> {
      while (true) {
        try {
          // Block forever
          Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
          // Continue the endless loop
        }
      }
    }, "testMetric");
    // Set AsyncGaugeConfig with max timeout of 100 ms, initial timeout of 10 ms and a daemon thread pool
    AsyncGauge.AsyncGaugeExecutor asyncGaugeExecutor = new AsyncGauge.AsyncGaugeExecutor.Builder()
        .setMaxMetricsMeasurementTimeoutInMs(100)
        .setInitialMetricsMeasurementTimeoutInMs(10)
        .setMetricMeasurementThreadCount(1)
        .setSlowMetricThresholdMs(50)
        .setSlowMetricMeasurementThreadCount(1)
        .build();
    MetricConfig metricConfig = new MetricConfig(asyncGaugeExecutor);
    // Test that caller of AsyncGauge.measure() will never be blocked
    gauge.measure(metricConfig, System.currentTimeMillis());
    // If the caller is blocked, the following line will never be executed
    System.out.println("Caller of AsyncGauge.measure() is not blocked");
  }

  @Test
  public void testAsyncGaugeExecutorWithFastSlowMetrics() throws InterruptedException {
    AtomicInteger callTimes = new AtomicInteger(0);
    MockTime mockTime = new MockTime();
    AtomicInteger sleepTimeInMs = new AtomicInteger(0);

    AsyncGauge fastMetric = new AsyncGauge(
        (ignored1, ignored2) -> {
          mockTime.sleep(sleepTimeInMs.get());
          return callTimes.incrementAndGet();
        } ,
        "fast_metric");
    AsyncGauge.AsyncGaugeExecutor asyncGaugeExecutor = new AsyncGauge.AsyncGaugeExecutor.Builder()
        .setMaxMetricsMeasurementTimeoutInMs(100)
        .setInitialMetricsMeasurementTimeoutInMs(10)
        .setMetricMeasurementThreadCount(1)
        .setSlowMetricThresholdMs(50)
        .setSlowMetricMeasurementThreadCount(1)
        .setInactiveSlowMetricCleanupThresholdInMs(100)
        .setInactiveSlowMetricCleanupIntervalInMs(100)
        .setTime(mockTime)
        .build();

    MetricConfig config = new MetricConfig(asyncGaugeExecutor);
    Assert.assertEquals(1.0d, fastMetric.measure(config, System.currentTimeMillis()), 0.0001d);
    // Intentionally slow down the metric collection to mark this metric as a slow metric
    sleepTimeInMs.set(200);
    Assert.assertEquals(2.0d, fastMetric.measure(config, System.currentTimeMillis()), 0.0001d);
    Assert.assertEquals(1, asyncGaugeExecutor.getSlowAsyncGaugeAccessMap().size());
    // Even the metric is being marked as slow, we should be able to collect the value.
    // Next try, we should get the cached value and the following retry should get a more recent value
    Assert.assertEquals(2.0d, fastMetric.measure(config, System.currentTimeMillis()), 0.0001d);
    // Sleep for some time to allow the async task to finish
    Thread.sleep(100);
    Assert.assertEquals(3.0d, fastMetric.measure(config, System.currentTimeMillis()), 0.0001d);
    Assert.assertEquals(1, asyncGaugeExecutor.getSlowAsyncGaugeAccessMap().size());
    // Reduce the metric collection time
    sleepTimeInMs.set(10);
    Thread.sleep(100);
    Assert.assertEquals(5.0d, fastMetric.measure(config, System.currentTimeMillis()), 0.0001d);
    Thread.sleep(100);
    Assert.assertEquals(6.0d, fastMetric.measure(config, System.currentTimeMillis()), 0.0001d);
    Assert.assertTrue(asyncGaugeExecutor.getSlowAsyncGaugeAccessMap().isEmpty());


    sleepTimeInMs.set(200);
    Assert.assertEquals(7.0d, fastMetric.measure(config, System.currentTimeMillis()), 0.0001d);
    Assert.assertEquals(1, asyncGaugeExecutor.getSlowAsyncGaugeAccessMap().size());
    mockTime.sleep(1000);
    // Wait for the metric cleanup thread to clear inactive slow metric
    Thread.sleep(1000);
    Assert.assertTrue(asyncGaugeExecutor.getSlowAsyncGaugeAccessMap().isEmpty());

    // Cancel long-running metric
    AtomicBoolean longRunningMetricInterrupted = new AtomicBoolean(false);
    AsyncGauge longRunningMetric = new AsyncGauge(
        (ignored1, ignored2) -> {
          try {
            Thread.sleep(100000); // 100s
          } catch (InterruptedException e) {
            longRunningMetricInterrupted.set(true);
            return 0;
          }
          return 1;
        } ,
        "long_running_metric"
    );
    mockTime.sleep(1000);
    // Return cached value
    Assert.assertEquals(0, longRunningMetric.measure(config, System.currentTimeMillis()), 0.0001d);
    // The long-running metric should time out in next invocation
    mockTime.sleep(1000);
    // Return error code because of timeout
    Assert.assertEquals(-1, longRunningMetric.measure(config, System.currentTimeMillis()), 0.0001d);
    Assert.assertTrue("Long running metric should be interrupted after hitting timeout", longRunningMetricInterrupted.get());
    Assert.assertEquals(1, asyncGaugeExecutor.getSlowAsyncGaugeAccessMap().size());
  }
}
