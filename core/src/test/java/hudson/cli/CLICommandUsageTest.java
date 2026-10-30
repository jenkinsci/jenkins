/*
 * The MIT License
 *
 * Copyright 2026 Jenkins Project Contributors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.mockito.MockedStatic;

class CLICommandUsageTest {

    public static class SampleCommand extends CLICommand {

        @Option(name = "-reason", usage = "Reason for quiet down")
        public String reason = "default-reason";

        @Option(name = "-timeout", usage = "Timeout in seconds")
        public int timeout = 42;

        @Argument(usage = "Positional target")
        public String target = "default-target";

        @Override
        public String getShortDescription() {
            return "Sample command description";
        }

        @Override
        protected int run() {
            return 0;
        }
    }

    @Test
    void usageHelpDisplaysOriginalDefaultValuesOnError() {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mocked.when(Jenkins::get).thenReturn(jenkins);

            SampleCommand cmd = new SampleCommand();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            // Provide custom values for options, followed by an unexpected extra argument
            // "unexpectedExtraArg"
            List<String> args = List.of(
                    "-reason", "custom-reason",
                    "-timeout", "999",
                    "provided-target",
                    "unexpectedExtraArg");

            int exitCode = cmd.main(
                    args,
                    Locale.ENGLISH,
                    new ByteArrayInputStream(new byte[0]),
                    new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    new PrintStream(stderr, true, StandardCharsets.UTF_8));

            assertEquals(2, exitCode, "CLI parsing should fail with exit code 2");

            String err = stderr.toString(StandardCharsets.UTF_8);

            // True defaults MUST be shown in the usage message
            assertThat("Usage should display the original default reason", err,
                    containsString("(default: default-reason)"));
            assertThat("Usage should display the original default timeout", err, containsString("(default: 42)"));
            assertThat("Usage should display the original default target", err,
                    containsString("(default: default-target)"));

            // Mutated user values MUST NOT be claimed to be defaults
            assertThat("Usage must not show mutated custom-reason as default", err,
                    not(containsString("(default: custom-reason)")));
            assertThat("Usage must not show mutated 999 as default", err, not(containsString("(default: 999)")));
            assertThat("Usage must not show mutated provided-target as default", err,
                    not(containsString("(default: provided-target)")));
        }
    }
}
