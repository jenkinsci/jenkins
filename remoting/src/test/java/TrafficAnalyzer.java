/**
 * See {@link hudson.remoting.TrafficAnalyzer}. This entry point makes it easier to
 * invoke the tool.
 *
 * @author Kohsuke Kawaguchi
 */
public class TrafficAnalyzer {
    public static void main(String[] args) throws Exception {
        hudson.remoting.TrafficAnalyzer.main(args);
    }
}
