package io.tehuti.benchmarks;

import io.tehuti.metrics.MetricsRepository;
import io.tehuti.metrics.Sensor;
import io.tehuti.metrics.stats.Avg;
import io.tehuti.metrics.stats.Max;
import io.tehuti.metrics.stats.Min;
import io.tehuti.metrics.stats.Percentile;
import io.tehuti.metrics.stats.Percentiles;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;


@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
public class MultiThreadedSensorRecording {

  @State(Scope.Benchmark)
  public static class SharedSensor {
    private final MetricsRepository syncMetricsRepository;
    private final Sensor syncSensor;

    public SharedSensor() {
      this.syncMetricsRepository = new MetricsRepository();
      this.syncSensor = getSensor(syncMetricsRepository.sensor("sync"));
    }

    private Sensor getSensor(Sensor sensor) {
      sensor.add("avg", new Avg());
      sensor.add("min", new Min());
      sensor.add("max", new Max());
      sensor.add("percentiles", new Percentiles(
          40000,
          10000,
          Percentiles.BucketSizing.LINEAR,
          new Percentile[] {
              new Percentile("p50", 50),
              new Percentile("p95", 95),
              new Percentile("p99", 99) }));

      return sensor;
    }
  }

  @Benchmark
  @Threads(value = 1)
  public void testSensor_01_Threads(Blackhole bh, SharedSensor sensor) {
    sensor.syncSensor.record();
  }

  @Benchmark
  @Threads(value = 2)
  public void testSensor_02_Threads(Blackhole bh, SharedSensor sensor) {
    sensor.syncSensor.record();
  }

  @Benchmark
  @Threads(value = 4)
  public void testSensor_04_Threads(Blackhole bh, SharedSensor sensor) {
    sensor.syncSensor.record();
  }

  @Benchmark
  @Threads(value = 8)
  public void testSensor_08_Threads(Blackhole bh, SharedSensor sensor) {
    sensor.syncSensor.record();
  }

  @Benchmark
  @Threads(value = 16)
  public void testSensor_16_Threads(Blackhole bh, SharedSensor sensor) {
    sensor.syncSensor.record();
  }

  public static void main(String[] args) throws RunnerException {
    org.openjdk.jmh.runner.options.Options opt = new OptionsBuilder()
        .include(MultiThreadedSensorRecording.class.getSimpleName())
        .addProfiler(GCProfiler.class)
        .build();
    new Runner(opt).run();
  }
}
