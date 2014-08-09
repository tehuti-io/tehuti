# tehuti
======

Tehuti is a simple metrics library providing statistical measurement, reporting and quota functionalities.

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

More documentation to come...
