package io.tehuti.metrics.stats;

import io.tehuti.metrics.MetricConfig;

import java.util.List;

/**
 * A {@link SampledStat} that maintains a simple count of what it has seen.
 *
 * Generally useless on its own, but useful as an intermediate result for other metrics, such as a {@link Rate}.
 */
public class SampledCount extends SampledStat {

    public SampledCount() {
        super(0);
    }

    @Override
    protected void update(Sample sample, double value, long now) {
        sample.incrementValue(1);
    }

    @Override
    public double combine(List<Sample> samples, MetricConfig config, long now) {
        double total = 0.0;
        for (int i = 0; i < samples.size(); i++)
            total += samples.get(i).getValue();
        return total;
    }

}
