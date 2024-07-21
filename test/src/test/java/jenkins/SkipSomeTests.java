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
        : "GIT_BRANCH environment variable is null"; // intentionally not a typical branch name

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
            if (JENKINS_URL.equals("https://ci.jenkins.io/")) {
                if (GIT_BRANCH.equals("master") || GIT_BRANCH.equals("origin/master")) {
                    /* Run 1 of 8 times on the master branch, typically once a week or more */
                    /* The master branch builds complete 8 times per week (on average), with each build running 3 configurations */
                    return (System.currentTimeMillis() % 8) == 0;
                }
            }
            return true;
        default:
            return true;
        }
    }
}
