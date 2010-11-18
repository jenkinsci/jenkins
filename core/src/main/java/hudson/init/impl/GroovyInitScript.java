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
import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

import hudson.model.Hudson;
import static hudson.init.InitMilestone.JOB_LOADED;
import hudson.init.Initializer;

/**
 * Run the initialization script, if it exists.
 * 
 * @author Kohsuke Kawaguchi
 */
public class GroovyInitScript {
    @Initializer(after=JOB_LOADED)
    public static void init(Hudson h) throws IOException {
        URL bundledInitScript = h.servletContext.getResource("/WEB-INF/init.groovy");
        if (bundledInitScript!=null) {
            LOGGER.info("Executing bundled init script: "+bundledInitScript);
            execute(new GroovyCodeSource(bundledInitScript));
        }

        File initScript = new File(h.getRootDir(),"init.groovy");
        if(initScript.exists()) {
            LOGGER.info("Executing "+initScript);
            execute(new GroovyCodeSource(initScript));
        }
    }

    private static void execute(GroovyCodeSource initScript) throws IOException {
        GroovyShell shell = new GroovyShell(Hudson.getInstance().getPluginManager().uberClassLoader);
        shell.evaluate(initScript);
    }

    private static final Logger LOGGER = Logger.getLogger(GroovyInitScript.class.getName());
}
