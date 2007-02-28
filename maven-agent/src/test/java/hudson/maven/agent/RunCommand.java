package hudson.maven.agent;

import hudson.remoting.Callable;

/**
 * Starts Maven CLI. Remotely executed.
 *
 * @author Kohsuke Kawaguchi
 */
public class RunCommand implements Callable {
    private final String[] args;

    public RunCommand(String... args) {
        this.args = args;
    }

    public Object call() throws Throwable {
        // return Main.class.getClassLoader().toString();
        return Main.launch(args);
    }
}
