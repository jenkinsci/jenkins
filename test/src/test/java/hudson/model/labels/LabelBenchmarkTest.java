package hudson.model.labels;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class LabelBenchmarkTest {
    @Test
    @Ignore(value="This is a benchmark, not a test")
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
                .include(LabelBenchmark.class.getName() + ".*");
        new Runner(options.build()).run();
        assertTrue(Files.exists(Paths.get("jmh-report.json")));
    }
}
