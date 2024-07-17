package jenkins;

/**
 * @author MarkEWaite
 */
public class SkipSomeTests {

    private static final String JENKINS_URL = System.getenv("JENKINS_URL") != null
            ? System.getenv("JENKINS_URL")
            : "http://localhost:8080/";

    private static final String GIT_BRANCH = System.getenv("GIT_BRANCH") != null
            ? System.getenv("GIT_BRANCH")
            : "origin/master";

    private static final long TEST_START_TIME = System.currentTimeMillis() / 1000L;

    public enum ReasonTestShouldRun {
        NEVER_FAILING_TEST, /* Test has not failed in a very long time, run it less often */
    }

    /**
     * Run a test sometimes, based on the reason the test should run.
     * Reduce costs by running tests less frequently when they use
     * this method in an Assume expression.
     */
    public static boolean runTestSometimes(ReasonTestShouldRun testReason) {
        switch (testReason) {
        case NEVER_FAILING_TEST:
            if (JENKINS_URL.contains("ci.jenkins.io")) {
                if (GIT_BRANCH.contains("master")) {
                    /* Run 1 of 15 times on the master branch, typically at least once a week */
                    /* The master branch builds 10-15 times per week, with each build running 3 configurations */
                    return (TEST_START_TIME % 15) == 0;
                } else {
                    /* Current ci.jenkins.io config runs 3 configurations. */
                    /* Run 1 of 3 times on non-master branch */
                    /* Tests are more likely to fail first on a pull request branch */
                    return (TEST_START_TIME % 3) == 0;
                }
            }
            return true;
        default:
            return true;
        }
    }
}
