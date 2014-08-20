# tehuti

Tehuti is a simple metrics library providing statistical measurement, reporting and quota functionalities.

## Build

    $ git clone git@github.com:FelixGV/tehuti.git
    $ cd tehuti
    $ ./gradlew build

## Usage

At its core, Tehuti has the concept of sensors, for recording data points, and metrics, for performing statistical measurements over those data points.

Here is an example usage:

    // set up metrics:
    MetricsRepository metrics = new MetricsRepository(); // this is the global repository of metrics and sensors
    Sensor sensor = metrics.sensor("message-sizes");
    sensor.add("message-sizes.avg", new Avg());
    sensor.add("message-sizes.count", new Count());
    sensor.add("message-sizes.max", new Max());
    sensor.add("message-sizes.qps-throughput", new OccurrenceRate());
    sensor.add("message-sizes.bytes-throughput", new Rate());
    sensor.add("message-sizes.total", new Total());
    sensor.add(new Percentiles(10 * 1024, // Histogram's memory consumption: max 10 KB
                               1024 * 1024, // Histogram's max value: 1 MB (i.e.: max expected message size)
                               Percentiles.BucketSizing.CONSTANT,
                               new Percentile("message-sizes.median", 50),
                               new Percentile("message-sizes.95thPercentile", 95),
                               new Percentile("message-sizes.99thPercentile", 99));

    // as messages are sent we record the sizes
    sensor.record(messageSize);

Note: you can also use metrics directly without sensors, although typically sensors are useful for the following reasons:

* Sensors offer a convenient way to make sure that several metrics are measured off of the same data point.
* Sensors are thread-safe. The synchronization happens at the sensor level, not the metric level.

### Configuration

One can define a MetricConfig and apply it at the sensor level, or for specific metrics. This configuration defines how sampling and quota works.

Here is an example of custom configuration for a sensor and a metric:

    // Initialize config so that sample windows roll over after 10 seconds or 1000 events, whichever comes first
    MetricConfig sensorConfig = new MetricConfig().timeWindow(10, TimeUnit.SECONDS).eventWindow(1000);
     
    // Initialize config so that there are 10 sample windows of 6000 milliseconds each
    MetricConfig specificConfig = new MetricConfig().samples(10).timeWindow(6000, TimeUnit.MILLISECONDS);
     
    // Use the first config for a sensor, and override one of the metrics within that sensor with the second config
    MetricsRepository metrics = new MetricsRepository(); // this is the global repository of metrics and sensors
    Sensor sensor = metrics.sensor("message-sizes", sensorConfig);
    sensor.add("message-sizes.min", new Min());
    sensor.add("message-sizes.max", new Max());
    sensor.add("message-sizes.avg", new Avg(), specificConfig);
     
    // as messages are sent we record the sizes
    sensor.record(messageSize);

Here are the configuration properties and their defaults:

* samples: The amount of sample windows to keep (and measure on). Once a sampled stat goes over this amount of samples, the oldest one will be purged and replaced by the new one. The default is 2 sample windows (the current and previous ones).
* eventWindow: The amount of events that can come into a single window before we roll forward to the next one. The default is Long.MAX_VALUE.
* timeWindow: The maximum time that can go by since the first event recorded in a window before we roll forward to the next one. The default is 30 seconds.
* quota: A Quota instance defining an upper or lower bound that must be respected for a certain metric. The default is null (no quota).

Once a sampled stat goes over its maximum amount of samples, the oldest sample will be replaced by the new one. When measuring the value of a sampled stat, any sample whose beginning time is older than samples * timeWindow will be purged, so that the measurement is not affected by old data points.

### Quotas

Tehuti also supports quotas. Quotas are passed within configurations, and can define a lower or upper bound for a specific metric. If the boundary is crossed, Tehuti will throw a QuotaViolationException. Applications can then catch (or bubble up) this exception to avoid doing an operation that would go beyond a desired threshold.

Here is an example on how to define quotas for queries per second and bytes per second off of the same sensor:

    // Initialize config so that we bound operations to 1000 QPS and 100 MBps
    MetricConfig queriesPerSecondQuota = new MetricConfig().quota(Quota.lessThan(1000))
    MetricConfig bytesPerSecondQuota = new MetricConfig().quota(Quota.lessThan(100 * 1024 * 1024))
     
    MetricsRepository metrics = new MetricsRepository(); // this is the global repository of metrics and sensors
    Sensor sensor = metrics.sensor("messages");
    sensor.add("message-sizes.min", new Min());
    sensor.add("message-sizes.max", new Max());
    sensor.add("message-sizes.avg", new Avg());
    sensor.add("message-sizes.count", new Count());
    sensor.add("message-sizes.qps-throughput", new OccurrenceRate(), queriesPerSecondQuota);
    sensor.add("message-sizes.bytes-throughput", new Rate(), bytesPerSecondQuota);
     
    // as messages are sent we record the sizes
    sensor.record(messageSize);

### Reporting

Finally, Tehuti can report metrics so that they can be picked up by external systems. Currently, Tehuti supports only JMX-based reporting, but the architecture is extendable to more types of reporting systems.

    MetricsRepository metrics = new MetricsRepository(); // this is the global repository of metrics and sensors
    metrics.addReporter(new JmxReporter("prefix.for.all.metrics.names."));
