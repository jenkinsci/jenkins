package hudson.cli;

import hudson.Extension;

/**
 * Show the list of all commands.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class HelpCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return "Lists all the available commands";
    }

    protected int run() {
        for (CLICommand c : CLICommand.all()) {
            stderr.println("  "+c.getName());
            stderr.println("    "+c.getShortDescription());
        }
        return 0;
    }
}
