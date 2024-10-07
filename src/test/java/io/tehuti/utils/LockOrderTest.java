package io.tehuti.utils;

import io.tehuti.metrics.JmxReporter;
import io.tehuti.metrics.MetricConfig;
import io.tehuti.metrics.MetricsRepository;
import io.tehuti.metrics.Sensor;
import io.tehuti.metrics.stats.Avg;
import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;


public class LockOrderTest {
  /**
   * Test to make sure operations take MetricsRepository lock first and the sensor lock after that.
   * The test should finish promptly without any deadlock.
   */
  @Test(timeout = 10 * Time.MS_PER_SECOND)
  public void testLockOrderOfMetricRepositoryAndSensor() throws Exception {
    MockTime time = new MockTime();
    MetricConfig config = new MetricConfig();
    MetricsRepository
        metricsRepository = new MetricsRepository(config, Arrays.asList(new JmxReporter()), time);

    Sensor s = metricsRepository.sensor("test.sensor");
    s.add("test.Avg", new Avg());

    ExecutorService executor = Executors.newFixedThreadPool(2);

    CyclicBarrier barrierForBothThreadsToRunTogether = new CyclicBarrier(2);
    CyclicBarrier barrierToWaitForBothThreadsToComplete = new CyclicBarrier(3);

    // thread 1
    executor.submit(() -> {
      try {
        barrierForBothThreadsToRunTogether.await();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      metricsRepository.removeSensor("test.sensor");
      try {
        barrierToWaitForBothThreadsToComplete.await();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // thread 2
    executor.submit(() -> {
      try {
        barrierForBothThreadsToRunTogether.await();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      s.add("test1.Avg", new Avg());
      try {
        barrierToWaitForBothThreadsToComplete.await();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    try {
      barrierToWaitForBothThreadsToComplete.await();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.MINUTES);
  }
}
