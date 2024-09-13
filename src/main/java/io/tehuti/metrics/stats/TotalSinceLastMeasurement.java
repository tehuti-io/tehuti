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
 * Simple version of {@link SampledTotal}, this aggregates total as a single sample and gets reset to 0 every time it is measured.
 * The sample window is just the time between measurements unlike SampledTotal which can be defined by number of events or elapsed time.
 * Measure every second to get total per second.
 * Measure every minute to get total per minute.
 * Measure adhoc to get the delta total since the last measurement.
 */
public class TotalSinceLastMeasurement extends Total implements Initializable {
    public TotalSinceLastMeasurement() {
        super();
    }

    public TotalSinceLastMeasurement(double value) {
        super(value);
    }

    /**
     * Return the total so far and reset it to 0.
     */
    @Override
    public double measure(MetricConfig config, long now) {
        double total = super.measure(config, now);
        super.reset();
        return total;
    }

    @Override
    public void init(MetricConfig config, long now) {
        if (config.quota() != null) {
            // quotas are not supported with resets in every measure(). Needs to be revisited to support quotas.
            throw new UnsupportedOperationException(TotalSinceLastMeasurement.class + " does not support quotas");
        }
    }
}