/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.args4j.Argument;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class CLICommandTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule();

    @Test
    public void ensureReasonIsUsed() throws Exception {
        logger.record(CLICommand.class, Level.FINE).capture(100);

        CLICommandInvoker invoker = new CLICommandInvoker(j, "with-reason");
        CLICommandInvoker.Result result = invoker.invoke();

        assertThat(result.returnCode(), is(StandardCLIReturnCode.OK.getCode()));
        assertThat(result.stdout(), allOf(
                not(containsString("REASON")),
                not(containsString("reasonProvided!"))
        ));
        assertThat(result.stderr(), containsString("REASON: reasonProvided!"));

        boolean hasReasonInLog = logger.getRecords().stream()
                .anyMatch(logRecord ->
                        logRecord.getMessage().contains("return the reason") &&
                                logRecord.getMessage().contains("reasonProvided!")
                );
        assertTrue("The log does not contain the provided reason by the command", hasReasonInLog);
    }

    @TestExtension("ensureReasonIsUsed")
    public static class WithReasonCommand extends CLICommand {
        @Override
        public String getShortDescription() {
            return null;
        }

        @Override
        protected CLIReturnCode execute() throws Exception {
            return new CLIReturnCode() {
                @Override
                public int getCode() {
                    return 0;
                }

                @Override
                public @CheckForNull
                String getReason(@Nonnull Locale locale) {
                    return "reasonProvided!";
                }
            };
        }
    }

    @Test
    public void ensureNoReason() throws Exception {
        logger.record(CLICommand.class, Level.FINE).capture(100);

        CLICommandInvoker invoker = new CLICommandInvoker(j, "without-reason");
        CLICommandInvoker.Result result = invoker.invoke();

        assertThat(result.returnCode(), is(StandardCLIReturnCode.OK.getCode()));
        assertThat(result.stdout(), not(containsString("REASON")));
        assertThat(result.stderr(), not(containsString("REASON")));

        boolean hasReasonInLog = logger.getRecords().stream()
                .anyMatch(logRecord -> logRecord.getMessage().contains("return the reason"));
        assertFalse("The log must not contain a reason message", hasReasonInLog);
    }

    @TestExtension("ensureNoReason")
    public static class WithoutReasonCommand extends CLICommand {
        @Override
        public String getShortDescription() {
            return null;
        }

        @Override
        protected CLIReturnCode execute() throws Exception {
            return new CLIReturnCode() {
                @Override
                public int getCode() {
                    return 0;
                }

                @Override
                public @CheckForNull
                String getReason(@Nonnull Locale locale) {
                    return null;
                }
            };
        }
    }

    @Test
    public void returnCodeIsUsed() throws Exception {
        logger.record(CLICommand.class, Level.FINE).capture(100);

        CLICommandInvoker invoker = new CLICommandInvoker(j, "return-code");

        Arrays.asList(-5, -1, 0, 1, 2, 120).forEach(desiredReturnCode -> {
            CLICommandInvoker.Result result = invoker.invokeWithArgs("r" + desiredReturnCode);
    
            assertThat(result.returnCode(), is(desiredReturnCode));
            assertThat(result.stdout(), not(containsString("REASON")));
            assertThat(result.stderr(), not(containsString("REASON")));
    
            boolean hasReasonInLog = logger.getRecords().stream()
                    .anyMatch(logRecord -> logRecord.getMessage().contains("return the reason"));
            assertFalse("The log must not contain a reason message", hasReasonInLog);
            
            // reset log
            logger.capture(100);
        });
    }

    @TestExtension("returnCodeIsUsed")
    public static class ReturnCodeCommand extends CLICommand {

        @Argument(required = true)
        public String desiredReturnCode;

        @Override
        public String getShortDescription() {
            return null;
        }

        @Override
        protected CLIReturnCode execute() throws Exception {
            return new CLIReturnCode() {
                @Override
                public int getCode() {
                    // remove the "r" otherwise the -5 will be considered as an option
                    return Integer.parseInt(desiredReturnCode.substring(1));
                }

                @Override
                public @CheckForNull String getReason(@Nonnull Locale locale) {
                    return null;
                }
            };
        }
    }

    @Test
    public void reasonIsLocalized() throws Exception {
        logger.record(CLICommand.class, Level.FINE).capture(100);

        CLICommandInvoker invoker = new CLICommandInvoker(j, "localized-reason");
        // default is Locale.ENGLISH
        checkReasonPresent(invoker.invoke(), "englishReason");

        // reset the log
        logger.capture(100);
        
        changeLocale(invoker, Locale.FRENCH);
        checkReasonPresent(invoker.invoke(), "otherReason");
    }
    
    private void changeLocale(CLICommandInvoker invoker, Locale desiredLocale) throws Exception {
        Field localeField = invoker.getClass().getDeclaredField("locale");
        localeField.setAccessible(true);
        localeField.set(invoker, desiredLocale);
    }

    @TestExtension("reasonIsLocalized")
    public static class LocalizedReasonCommand extends CLICommand {

        @Override
        public String getShortDescription() {
            return null;
        }

        @Override
        protected CLIReturnCode execute() throws Exception {
            return new CLIReturnCode() {
                @Override
                public int getCode() {
                    return 0;
                }

                @Override
                public @CheckForNull String getReason(@Nonnull Locale locale) {
                    return locale.equals(Locale.ENGLISH) ? "englishReason" : "otherReason";
                }
            };
        }
    }

    private void checkReasonPresent(CLICommandInvoker.Result result, String reason){
        assertThat(result.returnCode(), is(StandardCLIReturnCode.OK.getCode()));
        assertThat(result.stdout(), allOf(
                not(containsString("REASON")),
                not(containsString(reason))
        ));
        assertThat(result.stderr(), containsString("REASON: " + reason));

        boolean hasReasonInLog = logger.getRecords().stream()
                .anyMatch(logRecord ->
                        logRecord.getMessage().contains("return the reason") &&
                                logRecord.getMessage().contains(reason)
                );
        assertTrue("The log does not contain the provided reason by the command", hasReasonInLog);
    }
}
