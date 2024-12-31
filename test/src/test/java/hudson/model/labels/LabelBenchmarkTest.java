package hudson.model.labels;

import static org.junit.Assert.assertTrue;

import hudson.model.Label;
import hudson.agents.DumbAgent;
import hudson.agents.JNLPLauncher;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import jenkins.benchmark.jmh.JmhBenchmark;
import jenkins.benchmark.jmh.JmhBenchmarkState;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class LabelBenchmarkTest {
    @Test
    public void runBenchmark() throws Exception {
        // run the minimum possible number of iterations
        ChainedOptionsBuilder options = new OptionsBuilder()
                .mode(Mode.AverageTime)
                .forks(1)
                .result("jmh-report.json")
                .resultFormat(ResultFormatType.JSON)
                .operationsPerInvocation(1)
                .threads(1)
                .warmupForks(0)
                .warmupIterations(0)
                .measurementBatchSize(1)
                .measurementIterations(1)
                .timeUnit(TimeUnit.NANOSECONDS)
                .shouldFailOnError(true)
                .include(LabelBenchmarkTest.class.getName() + ".*");
        new Runner(options.build()).run();
        assertTrue(Files.exists(Paths.get("jmh-report.json")));
    }

    @JmhBenchmark
    public static class NodeLabelBenchmark {
        public static class StateImpl extends JmhBenchmarkState {
            @Override
            public void setup() throws Exception {
                DumbAgent test = new DumbAgent("test", "/tmp/agent", new JNLPLauncher());
                test.setLabelString("a b c");
                getJenkins().addNode(test);
            }
        }

        @Benchmark
        public void nodeGetAssignedLabels(StateImpl state, Blackhole blackhole) {
            blackhole.consume(state.getJenkins().getNode("test").getAssignedLabels());
        }
    }


    @JmhBenchmark
    public static class LabelBenchmark {
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

        @Benchmark
        public void jenkinsGetAssignedLabels(MyState state, Blackhole blackhole) {
            blackhole.consume(state.getJenkins().getAssignedLabels());
        }
    }
}
