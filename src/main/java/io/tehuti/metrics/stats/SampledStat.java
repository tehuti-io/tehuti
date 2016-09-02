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
public abstract class SampledStat implements MeasurableStat {

    protected double initialValue;
    private int current = 0;
    protected List<Sample> samples;

    public SampledStat(double initialValue) {
        this.initialValue = initialValue;
        this.samples = new ArrayList<Sample>(2);
    }

    @Override
    public void record(MetricConfig config, double value, long timeMs) {
        Sample sample = current(config, timeMs);
        if (sample.isComplete(timeMs, config))
            sample = advance(config, timeMs);
        update(sample, config, value, timeMs);
        sample.eventCount += 1;
    }

    private Sample advance(MetricConfig config, long timeMs) {
        this.current = (this.current + 1) % config.samples();
        if (this.current >= samples.size()) {
            Sample sample = newSample(timeMs);
            this.samples.add(sample);
            return sample;
        } else {
            Sample sample = current(config, timeMs);
            sample.reset(timeMs);
            return sample;
        }
    }

    protected Sample newSample(long timeMs) {
        return new Sample(this.initialValue, timeMs);
    }

    @Override
    public double measure(MetricConfig config, long now) {
        purgeObsoleteSamples(config, now);
        return combine(this.samples, config, now);
    }

    public Sample current(MetricConfig config, long timeMs) {
        checkInit(config, timeMs);
        return this.samples.get(this.current);
    }

    public Sample oldest(MetricConfig config, long now) {
        checkInit(config, now);
        Sample oldest = this.samples.get(0);
        for (int i = 1; i < this.samples.size(); i++) {
            Sample curr = this.samples.get(i);
            if (curr.lastWindowMs < oldest.lastWindowMs)
                oldest = curr;
        }
        return oldest;
    }

    /**
     * Checks that the sample windows are properly initialized.
     *
     * In case there are no initialized sample yet, this creates two windows: one that begins now, as well as the
     * previous window before that. This ensures that any measurement won't be calculated over an elapsed time of zero
     * or few milliseconds. This is particularly significant for {@link io.tehuti.metrics.stats.Rate}, which can
     * otherwise report disproportionately high values in those conditions. The downside of this approach is that
     * Rates will under-report their values initially by virtue of carrying one empty window in their samples.
     */
    private void checkInit(MetricConfig config, long now) {
        if (samples.size() == 0) {
            this.samples.add(newSample(now));
            this.samples.add(newSample(now - config.timeWindowMs()));
        }
    }

    protected abstract void update(Sample sample, MetricConfig config, double value, long timeMs);

    public abstract double combine(List<Sample> samples, MetricConfig config, long now);

    /**
     *  Timeout any windows that have expired in the absence of any events
     */
    protected void purgeObsoleteSamples(MetricConfig config, long now) {
        long expireAge = config.samples() * config.timeWindowMs();
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = this.samples.get(i);
            if (now - sample.lastWindowMs >= expireAge) {
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
        public double initialValue;
        public long eventCount;
        public long lastWindowMs;
        public double value;

        public Sample(double initialValue, long now) {
            this.initialValue = initialValue;
            this.eventCount = 0;
            this.lastWindowMs = now;
            this.value = initialValue;
        }

        public void reset(long now) {
            this.eventCount = 0;
            this.lastWindowMs = now;
            this.value = initialValue;
        }

        /**
         * @return a boolean indicating if the sample is past its time-based or event count-based limits.
         */
        public boolean isComplete(long timeMs, MetricConfig config) {
            return timeMs - lastWindowMs >= config.timeWindowMs() || eventCount >= config.eventWindow();
        }

        @Override
        public String toString() {
            return "Sample(initialValue = " + initialValue +
                    "; eventCount = " + eventCount +
                    "; lastWindowMs = " + lastWindowMs +
                    "; value = " + value + ")";
        }
    }

}
