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
package hudson.init.impl;

import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import static hudson.init.InitMilestone.JOB_LOADED;
import hudson.init.Initializer;
import java.util.logging.Level;

/**
 * Run the initialization script, if it exists.
 * 
 * @author Kohsuke Kawaguchi
 */
public class GroovyInitScript {
    @Initializer(after=JOB_LOADED)
    public static void init(Jenkins j) throws IOException {
        URL bundledInitScript = j.servletContext.getResource("/WEB-INF/init.groovy");
        if (bundledInitScript!=null) {
            LOGGER.info("Executing bundled init script: "+bundledInitScript);
            execute(new GroovyCodeSource(bundledInitScript));
        }

        File initScript = new File(j.getRootDir(),"init.groovy");
        if(initScript.exists()) {
            execute(initScript);
        }
        
        File initScriptD = new File(j.getRootDir(),"init.groovy.d");
        if (initScriptD.isDirectory()) {
            File[] scripts = initScriptD.listFiles(new FileFilter() {
                public boolean accept(File f) {
                    return f.getName().endsWith(".groovy");
                }
            });
            if (scripts!=null) {
                // sort to run them in a deterministic order
                Arrays.sort(scripts);
                for (File f : scripts)
                    execute(f);
            }
        }
    }

    private static void execute(File initScript) throws IOException {
        LOGGER.info("Executing "+initScript);
        execute(new GroovyCodeSource(initScript));
    }

    private static void execute(GroovyCodeSource initScript) {
        GroovyShell shell = new GroovyShell(Jenkins.getInstance().getPluginManager().uberClassLoader);
        try {
            shell.evaluate(initScript);
        } catch (RuntimeException x) {
            LOGGER.log(Level.WARNING, "Failed to run script " + initScript.getName(), x);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GroovyInitScript.class.getName());
}
