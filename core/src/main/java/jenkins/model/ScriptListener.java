package jenkins.model;

import hudson.ExtensionPoint;
import hudson.model.User;
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
     * @param u If available, the user that executed the script.
     */
    void onScriptFromConsole(String script, String runner, User u);

    /**
     * Called when a groovy script is executed from the CLI.
     *
     * @see hudson.cli.GroovyCommand#run()
     * @see hudson.cli.GroovyshCommand#run()
     * @param script The script to be executed.
     * @param u If available, the user that executed the script.
     */
    void onScriptFromCLI(String script, User u);

    /**
     * Called when a groovy script is executed from a source other than the CLI and Console.
     *
     * @see hudson.cli.GroovyCommand#run()
     * @see hudson.cli.GroovyshCommand#run()
     * @param script The script to be executed.
     * @param origin An identifiable origin that executed the script (Run ID, ...)
     */
    void onScriptFromOtherSource(String script, String origin);


    /**
     * Fires the {@link #onScriptFromConsole(String, String, User)} event to track the usage of the script console.
     *
     * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
     * @param script The script to be executed.
     * @param runner Descriptive name of the runner executing the script.
     * @param u If available, the user that executed the script.
     */
    static void fireScriptConsoleEvent(String script, String runner, User u) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptFromConsole(script, runner, u));
    }

    /**
     * Fires the {@link #onScriptFromCLI(String, User)} event to track the usage of the script console.
     *
     * @see hudson.cli.GroovyCommand#run()
     * @see hudson.cli.GroovyshCommand#run()
     * @param script The script to be executed.
     * @param u If available, the user that executed the script.
     */
    static void fireScriptFromCLIEvent(String script, User u) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptFromCLI(script, u));
    }

    /**
     * Fires the {@link #onScriptFromCLI(String, User)} event to track the usage of the script console.
     *
     * @see hudson.cli.GroovyCommand#run()
     * @see hudson.cli.GroovyshCommand#run()
     * @param script The script to be executed.
     * @param origin An identifiable origin that executed the script (Run ID, ...)
     */
    static void fireScriptFromOtherSourceEvent(String script, String origin) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScriptFromOtherSource(script, origin));
    }
}
