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

import io.tehuti.metrics.MeasurableStat;
import io.tehuti.metrics.MetricConfig;
import io.tehuti.utils.Time;

import java.util.concurrent.TimeUnit;

/**
 * The rate of the given quantity. By default this is the total observed over a set of samples from a sampled statistic
 * divided by the elapsed time over the sample windows. Alternative {@link SampledStat} implementations can be provided,
 * however, to record the rate of occurrences (e.g. the count of values measured over the time interval) or other such
 * values.
 */
public class Rate implements MeasurableStat {

    private final TimeUnit unit;
    private final SampledStat stat;

    public Rate() {
        this(TimeUnit.SECONDS);
    }

    public Rate(TimeUnit unit) {
        this(unit, new SampledTotal());
    }

    public Rate(SampledStat stat) {
        this(TimeUnit.SECONDS, stat);
    }

    public Rate(TimeUnit unit, SampledStat stat) {
        this.stat = stat;
        this.unit = unit;
    }

    public String unitName() {
        return unit.name().substring(0, unit.name().length() - 2).toLowerCase();
    }

    @Override
    public void record(MetricConfig config, double value, long timeMs) {
        this.stat.record(config, value, timeMs);
    }

    @Override
    public double measure(MetricConfig config, long now) {
        return internalMeasure(config, now, stat.measure(config, now));
    }

    @Override
    public double measureWithExtraValue(MetricConfig config, long now, double extraValue) {
        if (!(stat instanceof SampledTotal)) {
            throw new UnsupportedOperationException(
                "Do NOT support measure with extra value for stat: " + stat.getClass().getName());
        }
        double value = stat.measure(config, now) + extraValue;
        return internalMeasure(config, now, value);
    }

    private double internalMeasure(MetricConfig config, long now, double value) {
        if (value == 0) {
            return 0;
        } else {
            double elapsed = convert(now - stat.oldest(config, now).lastWindowMs);
            return value / elapsed;
        }
    }

    private double convert(long time) {
        switch (unit) {
            case NANOSECONDS:
                return time * Time.NS_PER_MS;
            case MICROSECONDS:
                return time * Time.US_PER_MS;
            case MILLISECONDS:
                return time;
            case SECONDS:
                return (double) time / Time.MS_PER_SECOND;
            case MINUTES:
                return (double) time / Time.MS_PER_MINUTE;
            case HOURS:
                return (double) time / Time.MS_PER_HOUR;
            case DAYS:
                return (double) time / Time.MS_PER_DAY;
            default:
                throw new IllegalStateException("Unknown unit: " + unit);
        }
    }
}