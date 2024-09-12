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
import io.tehuti.metrics.MetricConfig;


/**
 * Simple version of {@link SampledTotal}, this aggregates total as a single sample and gets reset to 0 every time it is measured.
 * The sample window is just the time between measurements unlike SampledTotal which can be defined by number of events or elapsed time.
 * Also, useful to get the total of an event and not have to keep track of the previous measurement to calculate the delta.
 */
public class SimpleSampledTotal extends Total {
    public SimpleSampledTotal() {
        super();
    }

    public SimpleSampledTotal(double value) {
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
}