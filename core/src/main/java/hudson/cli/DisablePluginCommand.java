/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.cli;

import hudson.Extension;
import hudson.PluginWrapper;
import hudson.lifecycle.RestartNotSupportedException;
import java.io.PrintStream;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Disable one or more installed plugins.
 * @since 2.151
 */
@Extension
public class DisablePluginCommand extends CLICommand {

    @Argument(metaVar = "plugin1 plugin2 plugin3", required = true, usage = "Plugins to be disabled.")
    private List<String> pluginNames;

    @Option(name = "-restart", aliases = "-r", usage = "Restart Jenkins after disabling plugins.")
    private boolean restart;

    @Option(name = "-strategy", aliases = "-s", metaVar = "strategy", usage = "How to process the dependent plugins. \n" +
            "- none: if a mandatory dependent plugin exists and it is enabled, the plugin cannot be disabled (default value).\n" +
            "- mandatory: all mandatory dependent plugins are also disabled, optional dependent plugins remain enabled.\n" +
            "- all: all dependent plugins are also disabled, no matter if its dependency is optional or mandatory.")
    private String strategy = PluginWrapper.PluginDisableStrategy.NONE.toString();

    @Option(name = "-quiet", aliases = "-q", usage = "Be quiet, print only the error messages")
    private boolean quiet;

    private static final int INDENT_SPACE = 3;

    @Override
    public String getShortDescription() {
        return Messages.DisablePluginCommand_ShortDescription();
    }

    // package-private access to be able to use it in the tests
    static final int RETURN_CODE_NOT_DISABLED_DEPENDANTS = 16;
    static final int RETURN_CODE_NO_SUCH_PLUGIN = 17;

    @Override
    protected void printUsageSummary(PrintStream stderr) {
        super.printUsageSummary(stderr);
        stderr.println(Messages.DisablePluginCommand_PrintUsageSummary());
    }

    @Override
    protected int run() throws Exception {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);

        // get the strategy as an enum
        PluginWrapper.PluginDisableStrategy strategyToUse;
        try {
            strategyToUse = PluginWrapper.PluginDisableStrategy.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException(
                    hudson.cli.Messages.DisablePluginCommand_NoSuchStrategy(
                            strategy,
                            String.format(
                                    "%s, %s, %s",
                                    PluginWrapper.PluginDisableStrategy.NONE,
                                    PluginWrapper.PluginDisableStrategy.MANDATORY,
                                    PluginWrapper.PluginDisableStrategy.ALL)),
                    iae);
        }

        // disable...
        List<PluginWrapper.PluginDisableResult> results = jenkins.pluginManager.disablePlugins(strategyToUse, pluginNames);

        // print results ...
        printResults(results);

        // restart if it was required and it's necessary (at least one plugin was disabled in this execution)
        restartIfNecessary(results);

        // return the appropriate error code
        return getResultCode(results);
    }

    /**
     * Print the result of all the process
     * @param results the list of results for the disablement of each plugin
     */
    private void printResults(List<PluginWrapper.PluginDisableResult> results) {
        for (PluginWrapper.PluginDisableResult oneResult : results) {
            printResult(oneResult, 0);
        }
    }

    /**
     * Print indented the arguments with the format passed beginning with the indent passed.
     * @param indent number of spaces at the beginning.
     * @param format format as in {@link String#format(String, Object...)}
     * @param arguments arguments to print as in {@link String#format(String, Object...)}
     */
    private void printIndented(int indent, String format, String... arguments) {
        if (indent == 0) {
            stdout.format(format + "%n", (Object[]) arguments);
        } else {
            String[] newArgs = new String[arguments.length + 1];
            newArgs[0] = " ";
            System.arraycopy(arguments, 0, newArgs, 1, arguments.length);

            String f = "%" + indent + "s" + format + "%n";
            stdout.format(f, (Object[]) newArgs);
        }
    }

    /**
     * Print the result of a plugin disablement with the indent passed.
     * @param oneResult the result of the disablement of a plugin.
     * @param indent the initial indent.
     */
    private void printResult(PluginWrapper.PluginDisableResult oneResult, int indent) {
        PluginWrapper.PluginDisableStatus status = oneResult.getStatus();
        if (quiet && (PluginWrapper.PluginDisableStatus.DISABLED.equals(status) || PluginWrapper.PluginDisableStatus.ALREADY_DISABLED.equals(status))) {
            return;
        }

        printIndented(indent, Messages.DisablePluginCommand_StatusMessage(oneResult.getPlugin(), oneResult.getStatus(), oneResult.getMessage()));
        if (!oneResult.getDependentsDisableStatus().isEmpty()) {
            indent += INDENT_SPACE;
            for (PluginWrapper.PluginDisableResult oneDependentResult : oneResult.getDependentsDisableStatus()) {
                printResult(oneDependentResult, indent);
            }
        }
    }

    /**
     * Restart if at least one plugin was disabled in this process.
     * @param results the list of results after the disablement of the plugins.
     */
    private void restartIfNecessary(List<PluginWrapper.PluginDisableResult> results) throws RestartNotSupportedException {
        if (restart) {
            for (PluginWrapper.PluginDisableResult oneResult : results) {
                if (restartIfNecessary(oneResult)) {
                    break;
                }
            }
        }
    }

    /**
     * Restart if this particular result of the disablement of a plugin and its dependent plugins (depending on the
     * strategy used) has a plugin disabled.
     * @param oneResult the result of a plugin (and its dependents).
     * @return true if it end up in restarting jenkins.
     */
    private boolean restartIfNecessary(PluginWrapper.PluginDisableResult oneResult) throws RestartNotSupportedException {
        PluginWrapper.PluginDisableStatus status = oneResult.getStatus();
        if (PluginWrapper.PluginDisableStatus.DISABLED.equals(status)) {
            Jenkins.get().safeRestart();
            return true;
        }

        if (!oneResult.getDependentsDisableStatus().isEmpty()) {
            for (PluginWrapper.PluginDisableResult oneDependentResult : oneResult.getDependentsDisableStatus()) {
                if (restartIfNecessary(oneDependentResult)) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Calculate the result code of the full process based in what went on during the process
     * @param results he list of results for the disablement of each plugin
     * @return the status code. 0 if all plugins disabled. {@link #RETURN_CODE_NOT_DISABLED_DEPENDANTS} if some
     * dependent plugin is not disabled (with strategy NONE), {@link #RETURN_CODE_NO_SUCH_PLUGIN} if some passed
     * plugin doesn't exist. Whatever happens first.
     */
    private int getResultCode(List<PluginWrapper.PluginDisableResult> results) {
        int result = 0;
        for (PluginWrapper.PluginDisableResult oneResult : results) {
            result = getResultCode(oneResult);
            if (result != 0) {
                break;
            }
        }

        return result;
    }

    /**
     * Calculate the result code of the disablement of one plugin based in what went on during the process of this one
     * and its dependent plugins.
     * @param result the result of the disablement of this plugin
     * @return the status code
     */
    private int getResultCode(PluginWrapper.PluginDisableResult result) {
        int returnCode = 0;
        switch (result.getStatus()) {
            case NOT_DISABLED_DEPENDANTS:
                returnCode = RETURN_CODE_NOT_DISABLED_DEPENDANTS;
                break;
            case NO_SUCH_PLUGIN:
                returnCode = RETURN_CODE_NO_SUCH_PLUGIN;
                break;
            default:
                for (PluginWrapper.PluginDisableResult oneDependentResult : result.getDependentsDisableStatus()) {
                    returnCode = getResultCode(oneDependentResult);
                    if (returnCode != 0) {
                        break;
                    }
                }
                break;
        }

        return returnCode;
    }
}
