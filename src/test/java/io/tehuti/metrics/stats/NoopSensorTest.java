package io.tehuti.metrics.stats;

import io.tehuti.Metric;
import io.tehuti.metrics.JmxReporter;
import io.tehuti.metrics.MetricConfig;
import io.tehuti.metrics.MetricsReporter;
import io.tehuti.metrics.MetricsRepository;
import io.tehuti.metrics.Sensor;
import io.tehuti.utils.MockTime;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link io.tehuti.metrics.NoopSensor}.
 */
public class NoopSensorTest {

    @Test
    public void testNoopSensorIsNotRegisteredAndHasName() {
        MockTime time = new MockTime();
        MetricConfig config = new MetricConfig();
        MetricsRepository repo = new MetricsRepository(config, Arrays.asList((MetricsReporter) new JmxReporter()), time);

        Sensor noop = repo.getNoopSensor("noop");

        // Name should be set
        assertEquals("noop", noop.name());
        // NoopSensor is not tracked/registered in the repository
        assertNull(repo.getSensor("noop"));
        // Repository should have no metrics
        assertTrue(repo.metrics().isEmpty());
    }

    @Test
    public void testRecordDoesNothing() {
        MockTime time = new MockTime();
        MetricConfig config = new MetricConfig();
        MetricsRepository repo = new MetricsRepository(config, Arrays.asList((MetricsReporter) new JmxReporter()), time);

        Sensor noop = repo.getNoopSensor("noop");

        // Invoke all record variants; should be no-ops and not throw
        noop.record();
        noop.record(1.23);
        noop.record(4.56, time.milliseconds());

        // Still no metrics registered/affected
        assertTrue(repo.metrics().isEmpty());
    }

    @Test
    public void testAddMethodsReturnNullOrEmptyAndDoNotRegisterMetrics() {
        MockTime time = new MockTime();
        MetricConfig config = new MetricConfig();
        MetricsRepository repo = new MetricsRepository(config, Arrays.asList((MetricsReporter) new JmxReporter()), time);

        Sensor noop = repo.getNoopSensor("noop");

        // MeasurableStat adds should return null and not register metrics
        Metric m1 = noop.add("test.avg", new Avg());
        Metric m2 = noop.add("test.avg2", "desc", new Avg());
        Metric m3 = noop.add("test.avg3", new Avg(), new MetricConfig());
        assertNull(m1);
        assertNull(m2);
        assertNull(m3);

        // CompoundStat adds should return empty map and not register metrics
        Percentiles percs = new Percentiles(10, 0.0, 100.0, Percentiles.BucketSizing.CONSTANT,
            new Percentile("noop.p50", 50.0));
        assertTrue(noop.add(percs, null).isEmpty());

        // Repository should still have no metrics registered
        assertTrue(repo.metrics().isEmpty());
        assertNull(repo.getMetric("test.avg"));
        assertNull(repo.getMetric("noop.p50"));
    }
}
