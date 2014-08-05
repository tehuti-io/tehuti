package org.tehuti.metrics.stats;

import org.tehuti.metrics.MetricConfig;

import java.util.List;

/**
 * A {@link SampledStat} that maintains a total of what it has seen.
 *
 * Generally useless on its own, but useful as an intermediate result for other metrics, such as a {@link Rate}.
 */
public class SampledTotal extends SampledStat {

    public SampledTotal() {
        super(0.0d);
    }

    @Override
    protected void update(Sample sample, MetricConfig config, double value, long timeMs) {
        sample.value += value;
    }

    @Override
    public double combine(List<Sample> samples, MetricConfig config, long now) {
        double total = 0.0;
        for (int i = 0; i < samples.size(); i++)
            total += samples.get(i).value;
        return total;
    }
}