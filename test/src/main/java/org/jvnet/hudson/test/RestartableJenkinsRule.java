package org.jvnet.hudson.test;

import groovy.lang.Closure;
import org.junit.rules.MethodRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a pattern for executing a sequence of steps.
 * In between steps, Jenkins gets restarted.
 *
 * <p>
 * To use this, add this rule instead of {@link JenkinsRule} to the test,
 * then from your test method, call {@link #step(Closure)} or {@link #addStep(Statement)} repeatedly.
 *
 * <p>
 * The rule will evaluate your test method to collect all steps, then execute them in turn and restart
 * Jenkins in between.
 *
 * @author Kohsuke Kawaguchi
 * @see JenkinsRule
 * @since 1.567
 */
public class RestartableJenkinsRule implements MethodRule {
    public JenkinsRule j;
    private Description description;
    private final List<Statement> steps = new ArrayList<Statement>();

    private TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Object that defines a test.
     */
    private Object target;

    /**
     * JENKINS_HOME
     */
    public File home;


    @Override
    public Statement apply(final Statement base, FrameworkMethod method, Object target) {
        this.description = Description.createTestDescription(
                method.getMethod().getDeclaringClass(), method.getName(), method.getAnnotations());

        this.target = target;

        return tmp.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // JENKINS_HOME needs to survive restarts, so we'll allocate our own
                home = tmp.newFolder();

                // test method will accumulate steps
                base.evaluate();
                // and we'll run them
                run();
            }
        }, description);
    }

    public void step(final Closure c) {
        addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                c.call(j);
            }
        });
    }

    public void addStep(final Statement step) {
        steps.add(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                j.jenkins.getInjector().injectMembers(step);
                j.jenkins.getInjector().injectMembers(target);
                step.evaluate();
            }
        });
    }

    private void run() throws Throwable {
        HudsonHomeLoader loader = new HudsonHomeLoader() {
            @Override
            public File allocate() throws Exception {
                return home;
            }
        };

        // run each step inside its own JenkinsRule
        for (Statement step : steps) {
            j = new JenkinsRule().with(loader);
            j.apply(step,description).evaluate();
        }
    }
}
