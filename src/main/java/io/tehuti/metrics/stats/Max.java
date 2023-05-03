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

import java.util.List;

import io.tehuti.metrics.MetricConfig;

/**
 * A {@link SampledStat} that gives the max over its samples.
 */
public final class Max extends SampledStat {

    public Max() {
        this(Double.NEGATIVE_INFINITY);
    }

    public Max(double initialValue) {
        super(initialValue);
    }

    @Override
    protected void update(Sample sample, double value, long now) {
        sample.setValue(Math.max(sample.getValue(), value));
    }

    @Override
    public double combine(List<Sample> samples, MetricConfig config, long now) {
        double max = initialValue;
        for (int i = 0; i < samples.size(); i++)
            max = Math.max(max, samples.get(i).getValue());
        return max;
    }

}
