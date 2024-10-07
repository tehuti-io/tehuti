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
import io.tehuti.metrics.CompoundStat.NamedMeasurable;
import io.tehuti.utils.Time;
import io.tehuti.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;


/**
 * A sensor applies a continuous sequence of numerical values to a set of associated metrics. For example a sensor on
 * message size would record a sequence of message sizes using the {@link #record(double)} api and would maintain a set
 * of metrics about request sizes such as the average or max.
 */
public class Sensor {
    /**
     * lock on {@link Sensor#registry} and {@link Sensor#lock} always needs to be taken in
     * same order, MetricsRepository first and then the Sensor lock.
     */
    private final MetricsRepository registry;
    private final ReentrantLock lock;
    private final String name;
    private final Sensor[] parents;
    private final List<Stat> stats;
    private final List<TehutiMetric> metrics;
    private final List<TehutiMetric> metricsWithPreCheckQuota;
    private final List<TehutiMetric> metricsWithPostCheckQuota;
    private final MetricConfig config;
    private final Time time;

    Sensor(MetricsRepository registry, String name, Sensor[] parents, MetricConfig config, Time time) {
        super();
        this.registry = Utils.notNull(registry);
        this.name = Utils.notNull(name);
        this.parents = parents == null ? new Sensor[0] : parents;
        this.metrics = new ArrayList<>();
        this.metricsWithPreCheckQuota = new ArrayList<>();
        this.metricsWithPostCheckQuota = new ArrayList<>();
        this.stats = new ArrayList<>();
        this.config = Utils.notNull(config);
        this.time = Utils.notNull(time);
        this.lock = new ReentrantLock();
        checkForest(new HashSet<>());
    }

    /** Validate that this sensor doesn't end up referencing itself */
    private void checkForest(Set<Sensor> sensors) {
        if (!sensors.add(this))
            throw new IllegalArgumentException("Circular dependency in sensors: " + name() + " is its own parent.");
        for (int i = 0; i < parents.length; i++)
            parents[i].checkForest(sensors);
    }

    /**
     * The name this sensor is registered with. This name will be unique among all registered sensors.
     */
    public String name() {
        return this.name;
    }

    /**
     * Record an occurrence, this is just short-hand for {@link #record(double) record(1.0)}
     */
    public void record() {
        record(1.0);
    }

    /**
     * Record a value with this sensor
     * @param value The value to record
     * @throws QuotaViolationException if recording this value moves a metric beyond its configured maximum or minimum
     *         bound
     */
    public void record(double value) {
        record(value, time.milliseconds());
    }

    /**
     * Record a value at a known time. This method is slightly faster than {@link #record(double)} since it will reuse
     * the time stamp.
     * @param value The value we are recording
     * @param timeMs The current POSIX time in milliseconds
     * @throws QuotaViolationException if recording this value moves a metric beyond its configured maximum or minimum
     *         bound
     */
    public void record(double value, long timeMs) {
        this.lock.lock();
        try {
            // Check quota before recording usage if needed.
            preCheckQuotas(timeMs, value);
            // increment all the stats
            for (int i = 0; i < this.stats.size(); i++) {
                this.stats.get(i).record(value, timeMs);
            }
            postCheckQuotas(timeMs);
        } finally {
            this.lock.unlock();
        }
        for (int i = 0; i < this.parents.length; i++) {
            this.parents[i].record(value, timeMs);
        }
    }

    /**
     * Check if we have violated our quota for any metric that has a configured quota
     * @param timeMs The current POSIX time in milliseconds
     */
    private void preCheckQuotas(long timeMs, double requestedValue) {
        if (this.metricsWithPreCheckQuota.isEmpty()) {
            return;
        }
        TehutiMetric metric;
        Quota quota;
        double value;
        for (int i = 0; i < this.metricsWithPreCheckQuota.size(); i++) {
            metric = this.metricsWithPreCheckQuota.get(i);
            quota = metric.config().quota();
            // If we check quota before recording, we should count on the value of the current request.
            // So we could prevent the usage of current request exceeding the quota.
            value = metric.extraValue(timeMs, requestedValue);
            if (!quota.acceptable(value)) {
                // TODO: Provide an alternative means to signal quota breaches which does not require exceptions
                throw new QuotaViolationException(
                    "Metric " + metric.name() + " is in violation of its " + quota, value);
            }
        }
    }

    /**
     * Check if we have violated our quota for any metric that has a configured quota
     * @param timeMs The current POSIX time in milliseconds
     */
    private void postCheckQuotas(long timeMs) {
        if (this.metricsWithPostCheckQuota.isEmpty()) {
            return;
        }
        TehutiMetric metric;
        Quota quota;
        double value;
        for (int i = 0; i < this.metricsWithPostCheckQuota.size(); i++) {
            metric = this.metricsWithPostCheckQuota.get(i);
            quota = metric.config().quota();
            value = metric.value(timeMs);
            if (!quota.acceptable(value)) {
                // TODO: Provide an alternative means to signal quota breaches which does not require exceptions
                throw new QuotaViolationException(
                    "Metric " + metric.name() + " is in violation of its " + quota, value);
            }
        }
    }

    /**
     * Register a compound statistic with this sensor with no config override. Equivalent to
     * {@link Sensor#add(CompoundStat, MetricConfig) add(stat, null)}
     */
    public Map<String, Metric> add(CompoundStat stat) {
        return add(stat, null);
    }

    /**
     * Register a compound statistic with this sensor which yields multiple measurable quantities (like a histogram)
     * @param stat The stat to register
     * @param config The configuration for this stat. If null then the stat will use the default configuration for this
     *        sensor.
     *
     * @return a map of {@link Metric} indexed by their name, each of which were contained in the {@link CompoundStat}
     *         and added to this sensor.
     */
    public Map<String, Metric> add(CompoundStat stat, MetricConfig config) {
        synchronized (this.registry) {
            this.lock.lock();
            try {
                this.stats.add(Utils.notNull(stat));
                MetricConfig statConfig = (config == null ? this.config : config);
                Map<String, Metric> addedMetrics = new HashMap<>();
                for (NamedMeasurable m : stat.stats()) {
                    TehutiMetric metric =
                        new TehutiMetric(this.lock, m.name(), m.description(), m.stat(), statConfig, time);
                    this.registry.registerMetric(metric);
                    addMetric(metric);
                    addedMetrics.put(metric.name(), metric);
                }
                if (stat instanceof Initializable) {
                    ((Initializable) stat).init(statConfig, time.milliseconds());
                }
                return addedMetrics;
            } finally {
                this.lock.unlock();
            }
        }
    }

    /**
     * Add a metric with default configuration and no description. Equivalent to
     * {@link Sensor#add(String, String, MeasurableStat, MetricConfig) add(name, "", stat, null)}
     */
    public Metric add(String name, MeasurableStat stat) {
        return add(name, stat, null);
    }

    /**
     * Add a metric with default configuration. Equivalent to
     * {@link Sensor#add(String, String, MeasurableStat, MetricConfig) add(name, description, stat, null)}
     */
    public Metric add(String name, String description, MeasurableStat stat) {
        return add(name, description, stat, null);
    }

    /**
     * Add a metric to this sensor with no description. Equivalent to
     * {@link Sensor#add(String, String, MeasurableStat, MetricConfig) add(name, "", stat, config)}
     */
    public Metric add(String name, MeasurableStat stat, MetricConfig config) {
        return add(name, "", stat, config);
    }

    /**
     * Register a metric with this sensor
     * @param name The name of the metric
     * @param description A description used when reporting the value
     * @param stat The statistic to keep
     * @param config A special configuration for this metric. If null use the sensor default configuration.
     * @return a {@link Metric} instance representing the registered metric
     */
    public Metric add(String name, String description, MeasurableStat stat, MetricConfig config) {
        synchronized (this.registry) {
            this.lock.lock();
            try {
                MetricConfig statConfig = (config == null ? this.config : config);
                TehutiMetric metric =
                    new TehutiMetric(this.lock, Utils.notNull(name), Utils.notNull(description), Utils.notNull(stat),
                        statConfig, time);
                this.registry.registerMetric(metric);
                addMetric(metric);
                this.stats.add(stat);
                return metric;
            } finally {
                this.lock.unlock();
            }
        }
    }

    /**
     * Unregister all metrics with this sensor
     */
    void removeAll() {
        synchronized (this.registry) {
            this.lock.lock();
            try {
                for (TehutiMetric metric : this.metrics) {
                    this.registry.unregisterMetric(metric);
                }
                this.metrics.clear();
                this.metricsWithPreCheckQuota.clear();
                this.metricsWithPostCheckQuota.clear();
                this.stats.clear();
            } finally {
                this.lock.unlock();
            }
        }
    }

    List<? extends Metric> metrics() {
        this.lock.lock();
        try {
            return Collections.unmodifiableList(this.metrics);
        } finally {
            this.lock.unlock();
        }
    }

    private void addMetric(TehutiMetric metric) {
        this.lock.lock();
        try {
            this.metrics.add(metric);
            if (metric.config().quota() != null) {
                if (metric.config().quota().isCheckQuotaBeforeRecording()) {
                    this.metricsWithPreCheckQuota.add(metric);
                } else {
                    this.metricsWithPostCheckQuota.add(metric);
                }
            }
        } finally {
            this.lock.unlock();
        }
    }
}
