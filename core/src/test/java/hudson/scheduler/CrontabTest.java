package hudson.scheduler;

import antlr.ANTLRException;

/**
 * @author Kohsuke Kawaguchi
 */
public class CrontabTest {
    public static void main(String[] args) throws ANTLRException {
        for (String arg : args) {
            CronTab ct = new CronTab(arg);
            System.out.println(ct.toString());
        }
    }
}
