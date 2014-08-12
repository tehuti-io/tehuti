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
    Metrics metrics = new Metrics(); // this is the global repository of metrics and sensors
    Sensor sensor = metrics.sensor("message-sizes");
    sensor.add("message-sizes.avg", new Avg());
    sensor.add("message-sizes.max", new Max());
     
    // as messages are sent we record the sizes
    sensor.record(messageSize);

Note: you can also use metrics directly without sensors, although typically sensors are useful for the following reasons:

* Sensors offer a convenient way to make sure that several metrics are measured off of the same data point.
* Sensors are thread-safe. The synchronization happens at the sensor level, not the metric level.

### Configuration

One can define a MetricConfig and apply it at the sensor level, or for specific metrics. This configuration defines how sampling and quota works.

For sampled stats, one can define the amount of sample windows to keep (and measure on); the default is 2 sample windows (the current and previous ones). One can also define two kinds of maximum bounds for sample windows: time-based and event-based; when the maximum time, or the maximum amount of events, is elapsed, a sampled stat will roll forward into a new window, and discard any expired window that goes beyond the maximum amount of windows to retain.

Here is an example of custom configuration for a sensor and a metric:

    // Initialize config so that sample windows roll over after 10 seconds or 1000 events, whichever comes first
    MetricConfig sensorConfig = new MetricConfig().timeWindow(10, TimeUnit.SECONDS).eventWindow(1000);
     
    // Initialize config so that there are 10 sample windows of 6000 milliseconds each
    MetricConfig specificConfig = new MetricConfig().samples(10).timeWindow(6000, TimeUnit.MILLISECONDS);
     
    // Use the first config for a sensor, and override one of the metrics within that sensor with the second config
    Metrics metrics = new Metrics(); // this is the global repository of metrics and sensors
    Sensor sensor = metrics.sensor("message-sizes", sensorConfig);
    sensor.add("message-sizes.min", new Min());
    sensor.add("message-sizes.max", new Max());
    sensor.add("message-sizes.avg", new Avg(), specificConfig);
     
    // as messages are sent we record the sizes
    sensor.record(messageSize);

### Quotas

Tehuti also supports quotas. Quotas are passed within configurations, and can define a lower or upper bound for a specific metric. If the boundary is crossed, Tehuti will throw a QuotaViolationException. Applications can then catch (or bubble up) this exception to avoid doing an operation that would go beyond a desired threshold.

Here is an example on how to define quotas for queries per second and bytes per second off of the same sensor:

    // Initialize config so that we bound operations to 1000 QPS and 100 MBps
    MetricConfig queriesPerSecondQuota = new MetricConfig().quota(Quota.lessThan(1000))
    MetricConfig bytesPerSecondQuota = new MetricConfig().quota(Quota.lessThan(100 * 1024 * 1024))
     
    Metrics metrics = new Metrics(); // this is the global repository of metrics and sensors
    Sensor sensor = metrics.sensor("messages");
    sensor.add("message-sizes.min", new Min());
    sensor.add("message-sizes.max", new Max());
    sensor.add("message-sizes.avg", new Avg());
    sensor.add("message-sizes.count", new Count());
    sensor.add("message-sizes.qps-throughput", new OccurrenceRate(), queriesPerSecondQuota);
    sensor.add("message-sizes.MBps-throughput", new Rate(), bytesPerSecondQuota);
     
    // as messages are sent we record the sizes
    sensor.record(messageSize);

### Reporting

Finally, Tehuti can report metrics so that they can be picked up by external systems. Currently, Tehuti supports only JMX-based reporting, but the architecture is extendable to more types of reporting systems.

    Metrics metrics = new Metrics(); // this is the global repository of metrics and sensors
    metrics.addReporter(new JmxReporter("prefix.for.all.metrics.names."));
