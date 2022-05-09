package jenkins.model;

import hudson.ExtensionPoint;
import jenkins.util.Listeners;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A listener to track Groovy scripts from the CLI and console.
 *
 * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
 * @see hudson.cli.GroovyCommand#run()
 * @see hudson.cli.GroovyshCommand#run()
 */
public interface ScriptListener extends ExtensionPoint {

    /**
     * Called when a groovy script is executed in Script console.
     *
     * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
     * @param script The script to be executed.
     * @param runner Descriptive name of the runner executing the script.
     */
    void onScriptFromConsole(String script, String runner);

    /**
     * Called when a groovy script is executed from the CLI.
     *
     * @see hudson.cli.GroovyCommand#run()
     * @see hudson.cli.GroovyshCommand#run()
     * @param script The script to be executed.
     */
    void onScriptFromCLI(String script);


    /**
     * Fires the {@link #onScriptFromConsole(String, String)} event to track the usage of the script console.
     *
     * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
     * @param script The script to be executed.
     * @param runner Descriptive name of the runner executing the script.
     */
    static void fireScriptConsoleEvent(String script, String runner) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptFromConsole(script, runner));
    }

    /**
     * Fires the {@link #onScriptFromCLI(String)} event to track the usage of the script console.
     *
     * @see hudson.cli.GroovyCommand#run()
     * @see hudson.cli.GroovyshCommand#run()
     * @param script The script to be executed.
     */
    static void fireScriptFromCLIEvent(String script) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptFromCLI(script));
    }
}
