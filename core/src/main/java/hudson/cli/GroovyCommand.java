/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.model.User;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.util.ScriptListener;
import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.Argument;

/**
 * Executes the specified groovy script.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.GroovyCommand_ShortDescription();
    }

    /**
     * Remaining arguments.
     */
    @Argument(metaVar = "ARGUMENTS", usage = "Command line arguments to pass into script. The first argument is ignored if it's exactly '=' for backward compatibility.")
    public List<String> remaining = new ArrayList<>();

    @Override
    protected int run() throws Exception {
        // this allows the caller to manipulate the JVM state, so require the execute script privilege.
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        final String scriptListenerCorrelationId = String.valueOf(System.identityHashCode(this));

        if (!remaining.isEmpty() && "=".equals(remaining.get(0))) {
            remaining.remove(0);
        }

        Binding binding = new Binding();
        binding.setProperty("out", new PrintWriter(new ScriptListener.ListenerWriter(new OutputStreamWriter(stdout, getClientCharset()), GroovyCommand.class, null, scriptListenerCorrelationId, User.current()), true));
        binding.setProperty("stdin", stdin);
        binding.setProperty("stdout", stdout);
        binding.setProperty("stderr", stderr);

        GroovyShell groovy = new GroovyShell(Jenkins.get().getPluginManager().uberClassLoader, binding);
        String script = IOUtils.toString(stdin);
        ScriptListener.fireScriptExecution(script, binding, GroovyCommand.class, null, scriptListenerCorrelationId, User.current());
        groovy.run(script, "RemoteClass", remaining.toArray(new String[0]));
        return 0;
    }
}
