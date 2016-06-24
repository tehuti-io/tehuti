package io.tehuti.metrics.stats;

import org.apache.log4j.Logger;
import org.junit.Test;
import io.tehuti.Metric;
import io.tehuti.metrics.*;
import io.tehuti.utils.MockTime;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RateTest {
    int minNumberOfSamples = 2;
    int maxNumberOfSamples = 20;
    long timeWindow = 10000;

    MockTime time = new MockTime();
    MetricsRepository metricsRepository = new MetricsRepository(new MetricConfig(), Arrays.asList((MetricsReporter) new JmxReporter()), time);

    Logger logger = Logger.getLogger(this.getClass());

    private MetricConfig getConfig(int samples) {
        return new MetricConfig().timeWindow(timeWindow, TimeUnit.MILLISECONDS).samples(samples);
    }

    @Test
    public void testReturnZeroWhenZeroRecordsAndElapsedTimeIsZero() {
        for (int samples = minNumberOfSamples; samples <= maxNumberOfSamples; samples++) {
            // Set up
            MetricConfig config = getConfig(samples);
            Sensor sensor = metricsRepository.sensor(
                    "testReturnZeroWhenZeroRecordsAndElapsedTimeIsZero.with" + samples + "samples", config);
            Metric rate = sensor.add(
                    "testReturnZeroWhenZeroRecordsAndElapsedTimeIsZero.with" + samples + "samples.qps", new OccurrenceRate());

            // No sleep, we measure the rate immediately after creation
            assertEquals("We should get zero QPS, not NaN [" + samples + " samples]", 0.0, rate.value(), 0.0);
        }
    }

    @Test
    public void testReturnSensibleValueWhenOneRecordAndElapsedTimeIsZero() {
        for (int samples = minNumberOfSamples; samples <= maxNumberOfSamples; samples++) {
            // Set up
            MetricConfig config = getConfig(samples);
            Sensor sensor = metricsRepository.sensor(
                    "testReturnZeroWhenOneRecordAndElapsedTimeIsZero.with" + samples + "samples", config);
            Metric rate = sensor.add(
                    "testReturnZeroWhenOneRecordAndElapsedTimeIsZero.with" + samples + "samples.qps", new OccurrenceRate());

            sensor.record(12345);

            // No sleep, we measure the rate immediately after recording
            double rateAtBeginningOfWindow = rate.value();
            double expectedResult = 1.0 / TimeUnit.SECONDS.convert(timeWindow, TimeUnit.MILLISECONDS);
            assertEquals("We should get low QPS for one data point at beginning of a window, " +
                    "not an absurdly high number [" + samples + " samples]",
                    expectedResult, rateAtBeginningOfWindow, 0.00001);
        }
    }

    @Test
    public void testReturnSensibleValuesAfterPopulatingAndThenPurgingAllWindows() {
        for (int samples = minNumberOfSamples; samples <= maxNumberOfSamples; samples++) {
            // Set up
            MetricConfig config = getConfig(samples);
            Sensor sensor = metricsRepository.sensor(
                    "testReturnSensibleValuesAfterPopulatingAndThenPurgingAllWindows.with" + samples + "samples", config);
            Metric rate = sensor.add(
                    "testReturnSensibleValuesAfterPopulatingAndThenPurgingAllWindows.with" + samples + "samples.qps", new OccurrenceRate());
            Metric count = sensor.add(
                    "testReturnSensibleValuesAfterPopulatingAndThenPurgingAllWindows.with" + samples + "samples.count", new Count());

            // This initializes all internal samples...
            // FIXME: Too implementation-specific, maybe we should always pre-init all samples?
            for (int i = 1; i <= samples; i++) {
                time.sleep(timeWindow);
                sensor.record(12345);
                double countOfRecords = count.value();
                assertEquals("We should have " + countOfRecords + " records so far [" + samples + " samples]",
                        i, countOfRecords, 0.0);
            }

            // Fast-forward in the future so that all samples will be marked obsolete
            time.sleep(timeWindow * samples + 1);
            assertEquals("We should get zero QPS, not NaN [" + samples + " samples]", 0.0, rate.value(), 0.0);

            // Record one data point with all samples obsolete
            sensor.record(12345);
            double rateAtBeginningOfWindow = rate.value();
            double expectedResult = 1.0 / TimeUnit.SECONDS.convert(timeWindow, TimeUnit.MILLISECONDS) / (samples - 1);
            assertEquals("We should get low QPS for one data point at beginning of a window, " +
                    "not an absurdly high number [" + samples + " samples]",
                    expectedResult, rateAtBeginningOfWindow, 0.00001);
        }
    }

    /**
     * This test case is used to verify the following issue:
     * Metric will report wrong value if the sensor time-window/samples are
     * different from the specific config for the associated metrics.
     * Sensor record function is using its own metric config instead of the
     * config controlled by each metric.
     * Considering the following scenario:
     * 1. Sensor is using the default 30s time-window;
     * 2. The metric associated with this sensor is using 2s time-window;
     * 3. When we record a value in the data sensor in the first second, the
     *    metric will report the correct value since both sensor record and metric
     *    measure functions are using the same window;
     * 4. If we record another value after 5 seconds, the sensor will still
     *    write to the same window because of the big 30s sensor time-window, but
     *    the metric measure function will treat the sensor time-window to be
     *    obsolete because the window was updated 5 seconds ago, then report 0;
     */
    @Test
    public void testSupportDifferentMetricAndSensorConfigs() {
        // Time window of sensor1 is 30s by default, and the time window of metricWith2Seconds is 2s.
        Sensor sensor1 = metricsRepository.sensor("rateSensor1");
        MetricConfig configWith2Seconds = new MetricConfig().timeWindow(2, TimeUnit.SECONDS);
        Metric metricWith2Seconds = sensor1.add("metric_with_2_seconds.test", new Rate(TimeUnit.SECONDS), configWith2Seconds);

        // Time window of sensor2 is 2s, and same as metricWithDefault2Seconds
        Sensor sensor2 = metricsRepository.sensor("rateSensor2", new MetricConfig().timeWindow(2, TimeUnit.SECONDS));
        Metric metricWithDefault2Seconds = sensor2.add("metric_with_default_2_seconds.test", new Rate(TimeUnit.SECONDS));

        sensor1.record(100);
        sensor2.record(100);
        assertEquals(metricWith2Seconds.value(), metricWithDefault2Seconds.value(), 0);

        time.sleep(1000);
        sensor1.record(100);
        sensor2.record(100);
        assertEquals(metricWith2Seconds.value(), metricWithDefault2Seconds.value(), 0);

        time.sleep(3000);
        sensor1.record(100);
        sensor2.record(100);
        assertEquals(metricWith2Seconds.value(), metricWithDefault2Seconds.value(), 0);
    }

}
