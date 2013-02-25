package hudson.maven.reporters;

import hudson.maven.MavenReporter;

/**
 * A maven reporter expressing whether he found test failures and the build should be marked as UNSTABLE.
 * 
 * @author Dominik Bartholdi (imod)
 * @since 1.496
 */
public abstract class TestFailureDetector extends MavenReporter {

    private static final long serialVersionUID = 1L;

    /**
     * Have any test failures been detected?
     * 
     * @return <code>true</code> if there are test failures
     */
    public abstract boolean hasTestFailures();

}
