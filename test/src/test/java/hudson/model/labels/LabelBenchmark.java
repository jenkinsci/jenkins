package hudson.model.labels;

import hudson.model.Label;
import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

@JmhBenchmark
public class LabelBenchmark {
    public static class MyState extends JmhBenchmarkState {
    }

    @Benchmark
    public void simpleLabel(MyState state, Blackhole blackhole) {
        blackhole.consume(Label.parse("some-label"));
    }

    @Benchmark
    public void complexLabel(MyState state, Blackhole blackhole) {
        blackhole.consume(Label.parse("label1 && label2"));
    }
}
