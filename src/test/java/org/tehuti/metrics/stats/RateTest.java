package org.tehuti.metrics.stats;

import org.apache.log4j.Logger;
import org.junit.Test;
import org.tehuti.Metric;
import org.tehuti.metrics.*;
import org.tehuti.utils.MockTime;

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

}
