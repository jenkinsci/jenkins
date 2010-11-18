/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

import java.util.Map;
import java.util.TreeMap;

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
        if (!Hudson.getInstance().hasPermission(Hudson.READ)) {
            stderr.println("You must authenticate to access this Hudson.\n"
                    + "Use --username/--password/--password-file parameters or login command.");
            return 0;
        }

        Map<String,CLICommand> commands = new TreeMap<String,CLICommand>();
        for (CLICommand c : CLICommand.all())
            commands.put(c.getName(),c);

        for (CLICommand c : commands.values()) {
            stderr.println("  "+c.getName());
            stderr.println("    "+c.getShortDescription());
        }
        
        return 0;
    }
}
