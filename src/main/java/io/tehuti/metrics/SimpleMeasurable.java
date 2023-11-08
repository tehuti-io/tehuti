package io.tehuti.metrics;

/**
 * A SimpleMeasurable doesn't care about time; the default implementation just does the measurement.
 */
public interface SimpleMeasurable extends Measurable {
  default double measure(MetricConfig config, long now) {
    return measure();
  }

  double measure();
}
