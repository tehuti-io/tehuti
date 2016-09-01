package io.tehuti.metrics.stats;

import io.tehuti.metrics.MeasurableStat;
import io.tehuti.metrics.MetricConfig;

/**
 * simplest, un-windowed metric that returns the value passed into it
 */
public class Gauge implements MeasurableStat {

  private double value;

  public Gauge() {
    this.value = Double.NaN;
  }

  public Gauge(double value) {
    this.value = value;
  }

  @Override
  public void record(MetricConfig config, double value, long now) {
    this.value = value;
  }

  @Override
  public double measure(MetricConfig config, long now) {
    return this.value;
  }
}
