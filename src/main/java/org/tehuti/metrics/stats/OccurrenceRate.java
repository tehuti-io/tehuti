package org.tehuti.metrics.stats;

import org.tehuti.metrics.MetricConfig;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to create a {@link Rate} based on a {@link SampledCount}.
 */
public class OccurrenceRate extends Rate {
    public OccurrenceRate() {
        super(TimeUnit.SECONDS);
    }

    public OccurrenceRate(TimeUnit unit) {
        super(unit, new SampledCount());
    }

    /**
     * A {@link SampledStat} that maintains a simple count of what it has seen.
     *
     * Generally useless except when used as part of a {@link Rate}.
     */
    public static class SampledCount extends SampledStat {

        public SampledCount() {
            super(0);
        }

        @Override
        protected void update(Sample sample, MetricConfig config, double value, long now) {
            sample.value += 1.0;
        }

        @Override
        public double combine(List<Sample> samples, MetricConfig config, long now) {
            double total = 0.0;
            for (int i = 0; i < samples.size(); i++)
                total += samples.get(i).value;
            return total;
        }

    }
}
