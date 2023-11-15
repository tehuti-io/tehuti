package io.tehuti.metrics.stats;

import static org.junit.Assert.assertEquals;

import io.tehuti.metrics.AsyncGaugeConfig;
import io.tehuti.metrics.MetricConfig;
import java.util.concurrent.Executors;
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
    AsyncGaugeConfig asyncGaugeConfig = new AsyncGaugeConfig(Executors.newSingleThreadExecutor(), 100, 10);
    MetricConfig metricConfig = new MetricConfig(asyncGaugeConfig);
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
    AsyncGaugeConfig asyncGaugeConfig = new AsyncGaugeConfig(Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r);
      thread.setDaemon(true);
      return thread;
    }), 100, 10);
    MetricConfig metricConfig = new MetricConfig(asyncGaugeConfig);
    // Test that caller of AsyncGauge.measure() will never be blocked
    gauge.measure(metricConfig, System.currentTimeMillis());
    // If the caller is blocked, the following line will never be executed
    System.out.println("Caller of AsyncGauge.measure() is not blocked");
  }
}
