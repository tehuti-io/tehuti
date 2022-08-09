/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.tehuti.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import io.tehuti.utils.Time;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import io.tehuti.Metric;
import io.tehuti.metrics.stats.*;
import io.tehuti.metrics.stats.Percentiles.BucketSizing;
import io.tehuti.utils.MockTime;
import org.junit.Test;

public class MetricsTest {

    private static double EPS = 0.000001;

    MockTime time = new MockTime();
    MetricConfig config = new MetricConfig();
    MetricsRepository metricsRepository = new MetricsRepository(config, Arrays.asList((MetricsReporter) new JmxReporter()), time);

    @Test
    public void testSimpleStats() throws Exception {
        ConstantMeasurable measurable = new ConstantMeasurable();
        metricsRepository.addMetric("direct.measurable", measurable);
        Sensor s = metricsRepository.sensor("test.sensor");
        s.add("test.avg", new Avg());
        s.add("test.max", new Max());
        s.add("test.min", new Min());
        s.add("test.rate", new Rate(TimeUnit.SECONDS));
        s.add("test.occurrence-rate", new OccurrenceRate());
        s.add("test.count", new Count());
        s.add(new Percentiles(100, -100, 100, BucketSizing.CONSTANT,
                new Percentile("test.median", 50.0),
                new Percentile("test.perc99_9", 99.9)));

        Sensor s2 = metricsRepository.sensor("test.sensor2");
        s2.add("s2.total", new Total());
        s2.record(5.0);

        for (int i = 0; i < 10; i++)
            s.record(i);

        // pretend 30 seconds passed...
        time.sleep(config.timeWindowMs());

        assertEquals("s2 reflects the constant value", 5.0, metricsRepository.getMetric("s2.total").value(), EPS);
        assertEquals("Avg(0...9) = 4.5", 4.5, metricsRepository.getMetric("test.avg").value(), EPS);
        assertEquals("Max(0...9) = 9", 9.0, metricsRepository.getMetric("test.max").value(), EPS);
        assertEquals("Min(0...9) = 0", 0.0, metricsRepository.getMetric("test.min").value(), EPS);
        assertEquals("Rate(0...9) = 1.5", 1.5, metricsRepository.getMetric("test.rate").value(), EPS);
        assertEquals("OccurrenceRate(0...9) = 0.33333333333", 0.33333333333, metricsRepository.getMetric("test.occurrence-rate").value(), EPS);
        assertEquals("Count(0...9) = 10", 10.0, metricsRepository.getMetric("test.count").value(), EPS);
    }

    @Test
    public void testHierarchicalSensors() {
        Sensor parent1 = metricsRepository.sensor("test.parent1");
        Metric parent1Count = parent1.add("test.parent1.count", new SampledCount());
        Sensor parent2 = metricsRepository.sensor("test.parent2");
        Metric parent2Count = parent2.add("test.parent2.count", new SampledCount());
        Sensor child1 = metricsRepository.sensor("test.child1", parent1, parent2);
        Metric child1Count = child1.add("test.child1.count", new SampledCount());
        Sensor child2 = metricsRepository.sensor("test.child2", parent1);
        Metric child2Count = child2.add("test.child2.count", new SampledCount());
        Sensor grandchild = metricsRepository.sensor("test.grandchild", child1);
        Metric grandchildCount = grandchild.add("test.grandchild.count", new SampledCount());

        /* increment each sensor one time */
        parent1.record();
        parent2.record();
        child1.record();
        child2.record();
        grandchild.record();

        double p1 = parent1Count.value();
        double p2 = parent2Count.value();
        double c1 = child1Count.value();
        double c2 = child2Count.value();
        double gc = grandchildCount.value();

        /* each metric should have a count equal to one + its children's count */
        assertEquals(1.0, gc, EPS);
        assertEquals(1.0 + gc, c1, EPS);
        assertEquals(1.0, c2, EPS);
        assertEquals(1.0 + c1, p2, EPS);
        assertEquals(1.0 + c1 + c2, p1, EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadSensorHiearchy() {
        Sensor p = metricsRepository.sensor("parent");
        Sensor c1 = metricsRepository.sensor("child1", p);
        Sensor c2 = metricsRepository.sensor("child2", p);
        metricsRepository.sensor("gc", c1, c2); // should fail
    }

    @Test
    public void testEventWindowing() {
        SampledCount sampledCount = new SampledCount();
        MetricConfig config = new MetricConfig().eventWindow(1).samples(2);
        sampledCount.record(config, 1.0, time.milliseconds());
        sampledCount.record(config, 1.0, time.milliseconds());
        assertEquals(2.0, sampledCount.measure(config, time.milliseconds()), EPS);
        sampledCount.record(config, 1.0, time.milliseconds()); // first event times out
        assertEquals(2.0, sampledCount.measure(config, time.milliseconds()), EPS);
    }

    @Test
    public void testTimeWindowing() {
        SampledCount sampledCount = new SampledCount();
        MetricConfig config = new MetricConfig().timeWindow(1, TimeUnit.MILLISECONDS).samples(2);
        sampledCount.record(config, 1.0, time.milliseconds());
        time.sleep(1);
        sampledCount.record(config, 1.0, time.milliseconds());
        assertEquals(2.0, sampledCount.measure(config, time.milliseconds()), EPS);
        time.sleep(1);
        sampledCount.record(config, 1.0, time.milliseconds()); // oldest event times out
        assertEquals(2.0, sampledCount.measure(config, time.milliseconds()), EPS);
    }

    @Test
    public void testOldDataHasNoEffect() {
        Max max = new Max();
        long windowMs = 100;
        int samples = 2;
        MetricConfig config = new MetricConfig().timeWindow(windowMs, TimeUnit.MILLISECONDS).samples(samples);
        max.record(config, 50, time.milliseconds());
        time.sleep(samples * windowMs);
        assertEquals(Double.NEGATIVE_INFINITY, max.measure(config, time.milliseconds()), EPS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDuplicateMetricName() {
        metricsRepository.sensor("test").add("test", new Avg());
        metricsRepository.sensor("test2").add("test", new Total());
    }

    @Test
    public void testQuotas() {
        Sensor sensor = metricsRepository.sensor("test");
        sensor.add("test1.total", new Total(), new MetricConfig().quota(Quota.lessThan(5.0)));
        sensor.add("test2.total", new Total(), new MetricConfig().quota(Quota.moreThan(0.0)));
        sensor.record(5.0);
        try {
            sensor.record(1.0);
            fail("Should have gotten a quota violation.");
        } catch (QuotaViolationException e) {
            // this is good
        }
        assertEquals(6.0, metricsRepository.metrics().get("test1.total").value(), EPS);
        sensor.record(-6.0);
        try {
            sensor.record(-1.0);
            fail("Should have gotten a quota violation.");
        } catch (QuotaViolationException e) {
            // this is good
        }
    }

    @Test
    public void testCheckQuotaBeforeRecording() {
        Sensor sensor = metricsRepository.sensor("test");
        double quota = 10.0;
        sensor.add("test1.total", new Total(),
            new MetricConfig().quota(Quota.lessThan(quota, true)));
        sensor.record(quota);
        try {
            sensor.record(1);
            fail("Should have gotten a quota violation.");
        } catch (QuotaViolationException e) {
            // expected
        }
        assertEquals(quota, metricsRepository.metrics().get("test1.total").value(), EPS);
    }

    @Test
    public void testCheckQuotaBeforeRecordingForRateSensor(){
        Sensor sensor = metricsRepository.sensor("test");
        double quota = 10.0;
        sensor.add("test1.total", new Rate(),
            new MetricConfig().quota(Quota.lessThan(quota, true)));
        // Should use all quota in current time window.
        sensor.record(quota * config.timeWindowMs() / Time.MS_PER_SECOND);
        // As we already used all quota, so could not accept any new value now.
        try {
            sensor.record(1);
            fail("Should have gotten a quota violation.");
        } catch (QuotaViolationException e) {
            // expected
        }

        // Sleep 1.5 time window
        time.sleep((long)(config.timeWindowMs() * 1.5));
        // Now we could use half quota more
        sensor.record(quota * config.timeWindowMs() /2 / Time.MS_PER_SECOND);
        // Sleep 1 time window, the oldest time window should be retired. We start a new time window
        time.sleep(config.timeWindowMs());
        sensor.record(quota * config.timeWindowMs() /2 / Time.MS_PER_SECOND);

    }

    @Test
    public void testCheckQuotaBeforeRecordingWithLargeSingleRequest() {
        Sensor sensor = metricsRepository.sensor("test");
        double quota = 10.0;
        sensor.add("test1.total", new Total(),
            new MetricConfig().quota(Quota.lessThan(quota, true)));

        try {
            sensor.record(quota*10);
            fail("Should have gotten a quota violation.");
        } catch (QuotaViolationException e) {
            // expected
        }
        sensor.record(1);
        // As we reject the large single request, so we did NOT record that usage.
        assertEquals(1, metricsRepository.metrics().get("test1.total").value(), EPS);
    }

    @Test
    public void testPercentiles() {
        int buckets = 100;
        Percentiles percs = new Percentiles(4 * buckets,
                                            0.0,
                                            100.0,
                                            BucketSizing.CONSTANT,
                                            new Percentile("test.p25", 25),
                                            new Percentile("test.p50", 50),
                                            new Percentile("test.p75", 75));
        MetricConfig config = new MetricConfig().eventWindow(50).samples(2);
        Sensor sensor = metricsRepository.sensor("test", config);
        sensor.add(percs);
        Metric p25 = this.metricsRepository.getMetric("test.p25");
        Metric p50 = this.metricsRepository.getMetric("test.p50");
        Metric p75 = this.metricsRepository.getMetric("test.p75");

        // record two windows worth of sequential values
        for (int i = 0; i < buckets; i++)
            sensor.record(i);

        assertEquals(25, p25.value(), 1.0);
        assertEquals(50, p50.value(), 1.0);
        assertEquals(75, p75.value(), 1.0);

        for (int i = 0; i < buckets; i++)
            sensor.record(0.0);

        assertEquals(0.0, p25.value(), 1.0);
        assertEquals(0.0, p50.value(), 1.0);
        assertEquals(0.0, p75.value(), 1.0);
    }

    @Test
    public void testRemoveMetrics() {
        Sensor sensor = metricsRepository.sensor("test");
        sensor.add("test.avg", new Avg());
        sensor.add("test.count", new Count());
        for (int i = 0; i < 10; i++)
            sensor.record(i);

        assertEquals("Avg(0...9) = 4.5", 4.5, metricsRepository.getMetric("test.avg").value(), EPS);
        assertEquals("Count(0...9) = 10", 10.0, metricsRepository.getMetric("test.count").value(), EPS);

        sensor.removeAll();

        assertNull(metricsRepository.getMetric("test.avg"));
        assertNull(metricsRepository.getMetric("test.count"));
    }

    public static class ConstantMeasurable implements Measurable {
        public double value = 0.0;

        @Override
        public double measure(MetricConfig config, long now) {
            return value;
        }

    }
}
