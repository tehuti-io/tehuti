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
package io.tehuti.metrics.stats;

import io.tehuti.metrics.Initializable;
import java.util.ArrayList;
import java.util.List;

import io.tehuti.metrics.MeasurableStat;
import io.tehuti.metrics.MetricConfig;

/*
  A SampledStat records a single scalar value measured over one or more samples. Each sample is recorded over a
  configurable window. The window can be defined by number of events or elapsed time (or both, if both are given the
  window is complete when <i>either</i> the event count or elapsed time criterion is met).

  All the samples are combined to produce the measurement. When a window is complete the oldest sample is cleared and
  recycled to begin recording the next sample.

     <-- sample -->  <-- sample -->  <-- sample -->  <-- sample -->  <-- sample -->  <-- sample -->
   +---------------+---------------+---------------+---------------+---------------+---------------+
   <------------------------------------- total sliding window ------------------------------------>

  The total sliding window is a circular list that contains numbers of sample.
  In a time-based sliding window, the length of the sliding window is "# of the sample" * "length of the sample".
  Since records that fall in the same sample will be aggregated, it is the "granularity" of the metric. Given a fix
  sliding window length, shortening the sample length makes a finer granularity. (more accurate)

  SampledStat is implemented in the lazy evaluation fashion. That being said the necessary widow update (removing
  obsolete samples) would not be done unless a query {@link SampledStat#measure(MetricConfig, long)} arrives.

  Subclasses of this class define different statistics measured using this basic pattern.
 */
public abstract class SampledStat implements MeasurableStat, Initializable {

    protected double initialValue;
    private int current = 0;
    protected List<Sample> samples;
    protected MetricConfig config;

    public SampledStat(double initialValue) {
        this.initialValue = initialValue;
        this.samples = new ArrayList<>(2);
    }

    @Override
    public void record(double value, long timeMs) {
        Sample sample = current();
        if (sample.isComplete(timeMs, this.config))
            sample = advance(timeMs);
        update(sample, value, timeMs);
    }

    private Sample advance(long timeMs) {
        this.current = nextSampleIndex();
        Sample sample = current();
        sample.reset(timeMs);
        return sample;
    }

    private int nextSampleIndex() {
        return (this.current + 1) % this.config.samples();
    }

    /** Called during initialization to pre-allocate all samples */
    protected Sample newSample(long timeMs) {
        return new Sample(this.initialValue, timeMs);
    }

    @Override
    public double measure(MetricConfig config, long now) {
        purgeObsoleteSamples(config, now);
        return combine(this.samples, config, now);
    }

    public Sample current() {
        return this.samples.get(this.current);
    }

    public Sample oldest() {
        Sample oldest = this.samples.get(0);
        for (int i = 1; i < this.samples.size(); i++) {
            Sample curr = this.samples.get(i);
            if (curr.getLastWindowMs() < oldest.getLastWindowMs())
                oldest = curr;
        }
        return oldest;
    }

    /**
     * Initializes the sample windows. Specifically, this creates two windows: one that begins now, as well as the
     * previous window before that. This ensures that any measurement won't be calculated over an elapsed time of zero
     * or few milliseconds. This is particularly significant for {@link io.tehuti.metrics.stats.Rate}, which can
     * otherwise report disproportionately high values in those conditions. The downside of this approach is that
     * Rates will under-report their values initially by virtue of carrying one empty window in their samples.
     */
    public void init(MetricConfig config, long now) {
        this.config = config;
        this.samples.add(newSample(now));
        for (int index = 1; index < config.samples(); index++) {
            this.samples.add(newSample(now - config.timeWindowMs()));
        }
    }

    protected abstract void update(Sample sample, double value, long timeMs);

    public abstract double combine(List<Sample> samples, MetricConfig config, long now);

    /**
     *  Timeout any windows that have expired in the absence of any events
     */
    protected void purgeObsoleteSamples(MetricConfig config, long now) {
        long expireAge = config.expirationAge();
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = this.samples.get(i);
            if (now - sample.getLastWindowMs() >= expireAge) {
                // The samples array is used as a circular list. The rank represents how many spots behind the current
                // window is window #i at. The current sample is rank 0, the next older sample is 1 and so on until
                // the oldest sample, which has a rank equal to samples.size() - 1.
                int rank = current - i;
                if (rank < 0) {
                    rank += samples.size();
                }
                // Here we reset the expired window to a time in the past that is offset proportionally to its rank.
                sample.reset(now - rank * config.timeWindowMs());
            }
        }
    }

    protected static class Sample {
        private final double initialValue;
        private long eventCount;
        private long lastWindowMs;
        private double value;

        public Sample(double initialValue, long now) {
            this.initialValue = initialValue;
            this.lastWindowMs = now;
            this.value = initialValue;
        }

        public void reset(long now) {
            this.eventCount = 0;
            this.lastWindowMs = now;
            this.value = this.initialValue;
        }

        public void setValue(double value) {
            this.value = value;
        }

        public void incrementValue(double increment) {
            this.value += increment;
        }

        public double getValue() {
            return this.value;
        }

        protected void incrementEventCount() {
            this.eventCount++;
        }

        public long getEventCount() {
            return this.eventCount;
        }

        public long getLastWindowMs() {
            return this.lastWindowMs;
        }

        /**
         * @return a boolean indicating if the sample is past its time-based limit.
         */
        public boolean isComplete(long timeMs, MetricConfig config) {
            return timeMs - lastWindowMs >= config.timeWindowMs();
        }

        @Override
        public String toString() {
            return "Sample(initialValue = " + initialValue +
                    "; lastWindowMs = " + lastWindowMs +
                    "; value = " + value + ")";
        }
    }
}
