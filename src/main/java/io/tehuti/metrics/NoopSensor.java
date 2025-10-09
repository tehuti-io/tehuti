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
package io.tehuti.metrics;

import io.tehuti.Metric;
import io.tehuti.utils.Time;
import java.util.Collections;
import java.util.Map;


/**
 * A sensor that does nothing.
 */
public class NoopSensor extends Sensor {
    NoopSensor(MetricsRepository registry, String name, Sensor[] parents, MetricConfig config, Time time) {
        super(registry, name, parents, config, time);
    }

    @Override
    public void record() {
        // NOOP
    }

    @Override
    public void record(double value) {
        // NOOP
    }

    @Override
    public void record(double value, long timeMs) {
       //NOOP
    }

    @Override
    public Map<String, Metric> add(CompoundStat stat, MetricConfig config) {
        return Collections.emptyMap();
    }

    @Override
    public Metric add(String name, String description, MeasurableStat stat, MetricConfig config) {
        return null;
    }
}
