package hudson.scheduler;

import antlr.ANTLRException;
import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class CrontabTest extends TestCase {
    public static void main(String[] args) throws ANTLRException {
        for (String arg : args) {
            CronTab ct = new CronTab(arg);
            System.out.println(ct.toString());
        }
    }

    public void test1() throws ANTLRException {
        new CronTab("@yearly");
        new CronTab("@weekly");
        new CronTab("@midnight");
        new CronTab("@monthly");
        new CronTab("0 0 * 1-10/3 *");
    }
}
