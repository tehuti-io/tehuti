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
    Metrics metrics = new Metrics(new MetricConfig(), Arrays.asList((MetricsReporter) new JmxReporter()), time);

    Logger logger = Logger.getLogger(this.getClass());

    @Test
    public void testReturnZeroWhenZeroRecordsAndElapsedTimeIsZero() {
        for (int samples = minNumberOfSamples; samples <= maxNumberOfSamples; samples++) {
            MetricConfig config = new MetricConfig().timeWindow(timeWindow, TimeUnit.MILLISECONDS).samples(samples);
            Sensor sensor = metrics.sensor("test.with" + samples + "samples.testAllSamplesPurged", config);
            Metric rate = sensor.add("test.with" + samples + "samples.testAllSamplesPurged.qps", new OccurrenceRate());
            assertEquals("We should get zero QPS, not NaN [" + samples + " samples]", 0.0, rate.value(), 0.0);
        }
    }

    @Test
    public void testReturnSensibleValuesAfterPurgingAllWindows() {
        for (int samples = minNumberOfSamples; samples <= maxNumberOfSamples; samples++) {
            MetricConfig config = new MetricConfig().timeWindow(timeWindow, TimeUnit.MILLISECONDS).samples(samples);
            Sensor sensor = metrics.sensor("test.with" + samples + "samples.testAllSamplesPurged", config);
            Metric rate = sensor.add("test.with" + samples + "samples.testAllSamplesPurged.qps", new OccurrenceRate());
            Metric count = sensor.add("test.with" + samples + "samples.testAllSamplesPurged.count", new Count());

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

            sensor.record(12345);
            double rateAtBeginningOfWindow = rate.value();
            double expectedResult = 1.0 / TimeUnit.SECONDS.convert(timeWindow, TimeUnit.MILLISECONDS) / (samples - 1);
            assertEquals("We should get low QPS for one data point at beginning of a window, " +
                    "not an absurdly high number [" + samples + " samples]",
                    expectedResult, rateAtBeginningOfWindow, 0.00001);
        }
    }

}
