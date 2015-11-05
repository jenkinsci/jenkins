/*
 * The MIT License
 *
 * Copyright 2013 Red Hat, Inc.
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

import hudson.Extension;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.GlobalMatrixAuthorizationStrategy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import jenkins.model.Jenkins;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Helper class to invoke {@link CLICommand} and check the response.
 *
 * @author ogondza
 */
public class CLICommandInvoker {

    private static final String username = "user";
    private final JenkinsRule rule;
    private final CLICommand command;

    private InputStream stdin;
    private List<String> args = Collections.emptyList();
    private List<Permission> permissions = Collections.emptyList();
    private Locale locale = Locale.ENGLISH;

    public CLICommandInvoker(final JenkinsRule rule, final CLICommand command) {

        if (command.getClass().getAnnotation(Extension.class) == null) {

            throw new AssertionError(String.format(
                    "Command %s is missing @Extension annotation.",
                    command.getClass()
            ));
        }

        this.rule = rule;
        this.command = command;
    }

    public CLICommandInvoker(final JenkinsRule rule, final String command) {
        this.rule = rule;
        this.command = CLICommand.clone(command);

        if (this.command == null) throw new AssertionError("No such command: " + command);
    }

    public CLICommandInvoker authorizedTo(final Permission... permissions) {

        this.permissions = Arrays.asList(permissions);
        return this;
    }

    public CLICommandInvoker withStdin(final InputStream stdin) {

        if (stdin == null) throw new NullPointerException("No stdin provided");

        this.stdin = stdin;
        return this;
    }

    public CLICommandInvoker withArgs(final String... args) {

        this.args = Arrays.asList(args);
        return this;
    }

    public Result invokeWithArgs(final String... args) {

        return withArgs(args).invoke();
    }

    public Result invoke() {

        setAuth();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();

        final int returnCode = command.main(
                args, locale, stdin, new PrintStream(out), new PrintStream(err)
        );

        return new Result(returnCode, out, err);
    }

    private void setAuth() {

        if (permissions.isEmpty()) return;

        JenkinsRule.DummySecurityRealm realm = rule.createDummySecurityRealm();
        realm.addGroups(username, "group");
        rule.jenkins.setSecurityRealm(realm);

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        for(Permission p: permissions) {
            p.setEnabled(true);
            auth.add(p, username);
        }
        rule.jenkins.setAuthorizationStrategy(auth);

        command.setTransportAuth(user().impersonate());
        // Otherwise it is SYSTEM, which would be relevant for a command overriding main:
        ACL.impersonate(Jenkins.ANONYMOUS);
    }

    public User user() {

        return User.get(username);
    }

    public static class Result {

        private final int result;
        private final ByteArrayOutputStream out;
        private final ByteArrayOutputStream err;

        private Result(
                final int result,
                final ByteArrayOutputStream out,
                final ByteArrayOutputStream err
        ) {

            this.result = result;
            this.out = out;
            this.err = err;
        }

        public int returnCode() {

            return result;
        }

        public String stdout() {

            return out.toString();
        }

        public String stderr() {

            return err.toString();
        }

        @Override
        public String toString() {

            StringBuilder builder = new StringBuilder("CLI command exited with ").append(result);
            String stdout = stdout();
            if (!"".equals(stdout)) {
                builder.append("\nSTDOUT:\n").append(stdout);
            }
            String stderr = stderr();
            if (!"".equals(stderr)) {
                builder.append("\nSTDERR:\n").append(stderr);
            }

            return builder.toString();
        }
    }

    public abstract static class Matcher extends TypeSafeMatcher<Result> {

        private final String description;

        private Matcher(String description) {
            this.description = description;
        }

        @Override
        protected void describeMismatchSafely(Result result, Description description) {
            description.appendText(result.toString());
        }

        public void describeTo(Description description) {
            description.appendText(this.description);
        }

        public static Matcher hasNoStandardOutput() {
            return new Matcher("No standard output") {
                @Override protected boolean matchesSafely(Result result) {
                    return "".equals(result.stdout());
                }
            };
        }

        public static Matcher hasNoErrorOutput() {
            return new Matcher("No error output") {
                @Override protected boolean matchesSafely(Result result) {
                    return "".equals(result.stderr());
                }
            };
        }

        public static Matcher succeeded() {
            return new Matcher("Exited with 0 return code") {
                @Override protected boolean matchesSafely(Result result) {
                    return result.result == 0;
                }
            };
        }

        public static Matcher succeededSilently() {
            return new Matcher("Succeeded silently") {
                @Override protected boolean matchesSafely(Result result) {
                    return result.result == 0 && "".equals(result.stderr()) && "".equals(result.stdout());
                }
            };
        }

        public static Matcher failedWith(final long expectedCode) {
            return new Matcher("Exited with " + expectedCode + " return code") {
                @Override protected boolean matchesSafely(Result result) {
                    return result.result == expectedCode;
                }
            };
        }
    }
}
