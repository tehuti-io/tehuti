package io.tehuti.metrics;

public interface Initializable {
  void init(MetricConfig config, long now);
}
