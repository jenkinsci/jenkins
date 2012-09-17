package jenkins.model;

/**
 * Access the package protected quiet period
 */
public class JenkinsAdaptor {
    public static void setQuietPeriod(Jenkins jenkins, int quietPeriod) {
        jenkins.quietPeriod = quietPeriod;
    }

}
