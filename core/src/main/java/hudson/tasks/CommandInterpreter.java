/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.Map;

/**
 * Common part between {@link Shell} and {@link BatchFile}.
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class CommandInterpreter extends Builder {
    /**
     * Command to execute. The format depends on the actual {@link CommandInterpreter} implementation.
     */
    protected final String command;

    public CommandInterpreter(String command) {
        this.command = command;
    }

    public final String getCommand() {
        return command;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        return perform(build,launcher,(TaskListener)listener);
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, TaskListener listener) throws InterruptedException {
        FilePath ws = build.getWorkspace();
        FilePath script=null;
        try {
            try {
                script = createScriptFile(ws);
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_UnableToProduceScript()));
                return false;
            }

            int r;
            try {
                EnvVars envVars = build.getEnvironment(listener);

                // logic which omits jobs using the default fallback jdk in matrix jobs
                if(build instanceof hudson.matrix.MatrixRun) {
                    // get project
                    hudson.matrix.MatrixProject project = ((hudson.matrix.MatrixRun) build).getParentBuild().getProject();

                    // check if any jdk is specified in axes
                    boolean skip = !project.getJDKs().isEmpty();

                    // check if we want to skip a default jdk run
                    skip &= project.isAvoidFallbackJDKs();

                    /*
                     * check if JAVA_HOME is set. if it is set the specified jdk is 
                     * available on the current computer. If not, the default jdk
                     * would be used.
                     */
                    String jdk = envVars.get("JAVA_HOME");
                    if(null != jdk && jdk.equals("") && skip) {
                        // set build result accordingly
                        build.setResult(Result.JDK_NOT_AVAILABLE);

                        // omit job triggering
                        return true;
                    }
                }

                // on Windows environment variables are converted to all upper case,
                // but no such conversions are done on Unix, so to make this cross-platform,
                // convert variables to all upper cases.
                for(Map.Entry<String,String> e : build.getBuildVariables().entrySet())
                    envVars.put(e.getKey(),e.getValue());

                r = launcher.launch().cmds(buildCommandLine(script)).envs(envVars).stdout(listener).pwd(ws).join();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_CommandFailed()));
                r = -1;
            }
            return r==0;
        } finally {
            try {
                if(script!=null)
                script.delete();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError(Messages.CommandInterpreter_UnableToDelete(script)) );
            }
        }
    }

    /**
     * Creates a script file in a temporary name in the specified directory.
     */
    public FilePath createScriptFile(FilePath dir) throws IOException, InterruptedException {
        return dir.createTextTempFile("hudson", getFileExtension(), getContents(), false);
    }

    public abstract String[] buildCommandLine(FilePath script);

    protected abstract String getContents();

    protected abstract String getFileExtension();
}
