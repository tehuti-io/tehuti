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
import io.tehuti.metrics.MetricConfig;

/**
 * Simple version of {@link SampledCount}, this aggregates counts as a single sample and gets reset to 0 every time it is measured.
 * The sample window is just the time between measurements unlike SampledCount which can be defined by number of events or elapsed time.
 * Measure every second to get count per second.
 * Measure every minute to get count per minute.
 * Measure adhoc to get the delta count since the last measurement.
 */
public class CountSinceLastMeasurement extends Count implements Initializable  {
    public CountSinceLastMeasurement() {
        super();
    }

    public CountSinceLastMeasurement(int value) {
        super(value);
    }

    /**
     * Return the count so far and reset it to 0.
     */
    @Override
    public double measure(MetricConfig config, long now) {
        double count = super.measure(config, now);
        super.reset();
        return count;
    }

    @Override
    public void init(MetricConfig config, long now) {
        if (config.quota() != null) {
            // quotas are not supported with resets in every measure(). Needs to be revisited to support quotas.
            throw new UnsupportedOperationException(CountSinceLastMeasurement.class + " does not support quotas");
        }
    }
}