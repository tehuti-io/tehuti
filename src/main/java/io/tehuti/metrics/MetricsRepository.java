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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.tehuti.Metric;
import io.tehuti.utils.SystemTime;
import io.tehuti.utils.Time;
import io.tehuti.utils.Utils;

/**
 * A registry of sensors and metrics.
 * <p>
 * A metric is a named, numerical measurement. A sensor is a handle to record numerical measurements as they occur. Each
 * Sensor has zero or more associated metrics. For example a Sensor might represent message sizes and we might associate
 * with this sensor a metric for the average, maximum, or other statistics computed off the sequence of message sizes
 * that are recorded by the sensor.
 * <p>
 * Usage looks something like this:
 *
 * <pre>
 * // set up metrics:
 * MetricsRepository metrics = new MetricsRepository(); // this is the global repository of metrics and sensors
 * Sensor sensor = metrics.sensor(&quot;message-sizes&quot;);
 * sensor.add(&quot;message-sizes.avg&quot;, new Avg());
 * sensor.add(&quot;message-sizes.max&quot;, new Max());
 *
 * // as messages are sent we record the sizes
 * sensor.record(messageSize);
 * </pre>
 */
public class MetricsRepository {

    private final MetricConfig config;
    private final ConcurrentMap<String, TehutiMetric> metrics;
    private final ConcurrentMap<String, Sensor> sensors;
    private final List<MetricsReporter> reporters;
    private final Time time;

    /**
     * Create a metrics repository with no metric reporters and default configuration.
     */
    public MetricsRepository() {
        this(new MetricConfig());
    }

    /**
     * Create a metrics repository with no metric reporters and default configuration.
     */
    public MetricsRepository(Time time) {
        this(new MetricConfig(), new ArrayList<MetricsReporter>(0), time);
    }

    /**
     * Create a metrics repository with no reporters and the given default config. This config will be used for any
     * metric that doesn't override its own config.
     * @param defaultConfig The default config to use for all metrics that don't override their config
     */
    public MetricsRepository(MetricConfig defaultConfig) {
        this(defaultConfig, new ArrayList<MetricsReporter>(0), new SystemTime());
    }

    /**
     * Create a metrics repository with a default config and the given metric reporters
     * @param defaultConfig The default config
     * @param reporters The metrics reporters
     * @param time The time instance to use with the metrics
     */
    public MetricsRepository(MetricConfig defaultConfig, List<MetricsReporter> reporters, Time time) {
        this.config = defaultConfig;
        this.sensors = new ConcurrentHashMap<>();
        this.metrics = new ConcurrentHashMap<>();
        this.reporters = Utils.notNull(reporters);
        this.time = time;
        for (MetricsReporter reporter : reporters)
            reporter.init(new ArrayList<TehutiMetric>());
    }

    /**
     * Get the sensor with the given name if it exists
     * @param name The name of the sensor
     * @return Return the sensor or null if no such sensor exists
     */
    public Sensor getSensor(String name) {
        return this.sensors.get(Utils.notNull(name));
    }

    /**
     * Get or create a sensor with the given unique name and no parent sensors.
     * @param name The sensor name
     * @return The sensor
     */
    public Sensor sensor(String name) {
        return sensor(name, null, (Sensor[]) null);
    }

    /**
     * Get or create a sensor with the given unique name and zero or more parent sensors. All parent sensors will
     * receive every value recorded with this sensor.
     * @param name The name of the sensor
     * @param parents The parent sensors
     * @return The sensor that is created
     */
    public Sensor sensor(String name, Sensor... parents) {
        return sensor(name, null, parents);
    }

    /**
     * Get or create a sensor with the given unique name and zero or more parent sensors. All parent sensors will
     * receive every value recorded with this sensor.
     * @param name The name of the sensor
     * @param config A default configuration to use for this sensor for metrics that don't have their own config
     * @param parents The parent sensors
     * @return The sensor that is created
     */
    public synchronized Sensor sensor(String name, MetricConfig config, Sensor... parents) {
        Sensor s = getSensor(name);
        if (s == null) {
            s = new Sensor(this, name, parents, config == null ? this.config : config, time);
            this.sensors.put(name, s);
        }
        return s;
    }

    /**
     * Add a metric to monitor an object that implements measurable. This metric won't be associated with any sensor.
     * This is a way to expose existing values as metrics.
     * @param name The name of the metric
     * @param measurable The measurable that will be measured by this metric
     * @return a {@link Metric} instance representing the registered metric
     */
    public Metric addMetric(String name, Measurable measurable) {
        return addMetric(name, "", measurable);
    }

    /**
     * Add a metric to monitor an object that implements measurable. This metric won't be associated with any sensor.
     * This is a way to expose existing values as metrics.
     * @param name The name of the metric
     * @param description A human-readable description to include in the metric
     * @param measurable The measurable that will be measured by this metric
     * @return a {@link Metric} instance representing the registered metric
     */
    public Metric addMetric(String name, String description, Measurable measurable) {
        return addMetric(name, description, null, measurable);
    }

    /**
     * Add a metric to monitor an object that implements measurable. This metric won't be associated with any sensor.
     * This is a way to expose existing values as metrics.
     * @param name The name of the metric
     * @param config The configuration to use when measuring this measurable
     * @param measurable The measurable that will be measured by this metric
     * @return a {@link Metric} instance representing the registered metric
     */
    public Metric addMetric(String name, MetricConfig config, Measurable measurable) {
        return addMetric(name, "", config, measurable);
    }

    /**
     * Add a metric to monitor an object that implements measurable. This metric won't be associated with any sensor.
     * This is a way to expose existing values as metrics.
     * @param name The name of the metric
     * @param description A human-readable description to include in the metric
     * @param config The configuration to use when measuring this measurable
     * @param measurable The measurable that will be measured by this metric
     * @return a {@link Metric} instance representing the registered metric
     *
     * N.B.: If the registered measurable is a {@link CompoundStat} (such as {@link io.tehuti.metrics.stats.Percentiles}),
     *       then the returned {@link Metric} will represent that {@link CompoundStat}. In order to get access to the
     *       {@link Metric} instances representing the specific metrics contained in the {@link CompoundStat}, you'll
     *       need to call {@link #getMetric(String)} or {@link #metrics()}.
     */
    public synchronized Metric addMetric(String name, String description, MetricConfig config, Measurable measurable) {
        TehutiMetric m = new TehutiMetric(new Object(),
                                          Utils.notNull(name),
                                          Utils.notNull(description),
                                          Utils.notNull(measurable),
                                          config == null ? this.config : config,
                                          time);
        registerMetric(m);
        return m;
    }

    /**
     * Add a MetricReporter
     */
    public synchronized void addReporter(MetricsReporter reporter) {
        Utils.notNull(reporter).init(new ArrayList<TehutiMetric>(metrics.values()));
        this.reporters.add(reporter);
    }

    synchronized void registerMetric(TehutiMetric metric) {
        if (this.metrics.containsKey(metric.name()))
            throw new IllegalArgumentException("A metric named '" + metric.name() + "' already exists, can't register another one.");
        this.metrics.put(metric.name(), metric);
        for (MetricsReporter reporter : reporters)
            reporter.addMetric(metric);
    }

    /**
     * Get all the metrics currently maintained indexed by metric name
     * @return A map of all metrics in this metricsRepository
     */
    public Map<String, ? extends Metric> metrics() {
        return this.metrics;
    }

    public Metric getMetric(String name) {
        return this.metrics.get(name);
    }

    /**
     * Close this metrics repository.
     */
    public void close() {
        for (MetricsReporter reporter : this.reporters)
            reporter.close();
    }

}
