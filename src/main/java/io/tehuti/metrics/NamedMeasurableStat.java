package io.tehuti.metrics;

/**
 * A NamedMeasurableStat is a MeasurableStat that has a name.
 */
public interface NamedMeasurableStat extends MeasurableStat {
  String getStatName();
}
