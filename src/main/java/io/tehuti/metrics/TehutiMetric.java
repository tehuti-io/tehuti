/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tehuti.metrics;

import io.tehuti.Metric;
import io.tehuti.metrics.stats.SampledStat;
import io.tehuti.utils.Time;
import io.tehuti.utils.Utils;
import java.util.concurrent.locks.ReentrantLock;


/**
 * This implementation of the {@link io.tehuti.Metric} interface is meant to be used internally by Tehuti.
 *
 * It should not be exposed to users of the library.
 */

public final class TehutiMetric implements Metric {

    private final String name;
    private final String description;
    private final ReentrantLock lock;
    private final Time time;
    private final Measurable measurable;
    private final MetricConfig config;

    TehutiMetric(ReentrantLock lock, String name, String description, Measurable measurable, MetricConfig config, Time time) {
        super();
        this.name = name;
        this.description = description;
        this.lock = lock;
        this.measurable = measurable;
        this.config = Utils.notNull(config);
        this.time = time;
        if (this.measurable instanceof Initializable) {
            ((Initializable) this.measurable).init(config, time.milliseconds());
        }
    }

    MetricConfig config() {
        return this.config;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public double value() {
        return value(time.milliseconds());
    }

    /**
     * Return the current value of the measurement.
     *
     * @param timeMs
     *
     * @return
     */
    double value(long timeMs) {
        this.lock.lock();
        try {
            return this.measurable.measure(config, timeMs);
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Return the current value plus extra value we gave of this measurement.
     *
     * @param timeMs
     * @param extraValue
     *
     * @return
     */
    double extraValue(long timeMs, double extraValue) {
        this.lock.lock();
        try {
            return this.measurable.measureWithExtraValue(config, timeMs, extraValue);
        } finally {
            this.lock.unlock();
        }
    }
}
