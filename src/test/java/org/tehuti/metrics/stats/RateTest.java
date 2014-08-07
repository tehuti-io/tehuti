package org.tehuti.metrics.stats;

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

    @Test
    public void testReturnZeroWhenElapsedTimeIsZero() {
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
            assertEquals("We should get zero QPS, not NaN [" + samples + " samples]", 0.0, rate.value(), 0.0);
            sensor.record(12345);
//            time.sleep(1000);
//            assertEquals("We should get 1 QPS after one second [" + samples + " samples]", 1.0, rate.value(), 0.0);

//            time.sleep(timeWindow * samples - 1000 + 1);
            time.sleep(timeWindow * samples + 1);
            assertEquals("We should get zero QPS, not NaN [" + samples + " samples]", 0.0, rate.value(), 0.0);

            sensor.record(12345);
            time.sleep(1);
            double rateAtBeginningOfWindow = rate.value();
            assertTrue("We should get low QPS for one data point at beginning of a window, not an absurdly high number (" +
                    rateAtBeginningOfWindow  + ") [" + samples + " samples]",
                    rateAtBeginningOfWindow < 1.0);
        }
    }

}
