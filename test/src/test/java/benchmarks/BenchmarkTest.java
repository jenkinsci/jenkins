package benchmarks;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkTest {
    /**
     * Runs a sample benchmark to make sure that benchmarks execute successfully and generate a report.
     * <p>
     * To run your benchmarks, create a benchmark runner similar to this class and use
     * {@link jenkins.benchmark.jmh.BenchmarkFinder} to automatically find classes for benchmark which are annotated
     * with {@link jenkins.benchmark.jmh.JmhBenchmark}.
     * @throws Exception when the benchmark fails to run or throws an exception.
     * @see <a href="https://www.jenkins.io/blog/2019/06/21/performance-testing-jenkins/">Blog post on writing benchmarks</a>
     */
    @Test
    public void runSampleBenchmark() throws Exception {
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
                                            .timeUnit(TimeUnit.MICROSECONDS)
                                            .shouldFailOnError(true)
                                            // just run the SampleBenchmark, not other benchmarks
                                            .include(SampleBenchmark.class.getName() + ".*");
        new Runner(options.build()).run();
        assertTrue(Files.exists(Paths.get("jmh-report.json")));
    }
}
