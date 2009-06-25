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

import hudson.Extension;
import hudson.model.Hudson;
import org.codehaus.groovy.tools.shell.Groovysh;
import org.codehaus.groovy.tools.shell.IO;

import java.util.List;
import java.io.PrintStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.PrintWriter;

import jline.UnsupportedTerminal;
import jline.Terminal;

/**
 * Executes Groovy shell.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyshCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return "Runs an interactive groovy shell";
    }

    @Override
    public int main(List<String> args, InputStream stdin, PrintStream stdout, PrintStream stderr) {
        // this allows the caller to manipulate the JVM state, so require the admin privilege.
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

        // this being remote means no jline capability is available
        System.setProperty("jline.terminal", UnsupportedTerminal.class.getName());
        Terminal.resetTerminal();

        Groovysh shell = new Groovysh(new IO(new BufferedInputStream(stdin),stdout,stderr));
        // redirect "println" to the CLI
        shell.getInterp().getContext().setProperty("out",new PrintWriter(stdout,true));
        return shell.run(args.toArray(new String[args.size()]));
    }

    protected int run() {
        throw new UnsupportedOperationException();
    }
}
