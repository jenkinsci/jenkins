package benchmarks;

import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

/**
 * A sample benchmark.
 */
@JmhBenchmark
public class SampleBenchmark {
    public static class MyState extends JmhBenchmarkState {
    }

    @Benchmark
    public void benchmark(MyState state, Blackhole blackhole) {
        blackhole.consume(state.getJenkins().getSystemMessage());
    }
}
