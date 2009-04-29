package hudson.cli;

import hudson.Extension;
import org.codehaus.groovy.tools.shell.Groovysh;
import org.codehaus.groovy.tools.shell.IO;

import java.util.List;
import java.io.PrintStream;
import java.io.InputStream;
import java.io.BufferedInputStream;

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
    public int main(List<String> args, InputStream stdin, PrintStream stdout, PrintStream stderr) {
        // this being remote means no jline capability is available
        System.setProperty("jline.terminal", UnsupportedTerminal.class.getName());
        Terminal.resetTerminal();

        Groovysh shell = new Groovysh(new IO(new BufferedInputStream(stdin),stdout,stderr));
        return shell.run(args.toArray(new String[args.size()]));
    }

    protected int run() {
        throw new UnsupportedOperationException();
    }
}
