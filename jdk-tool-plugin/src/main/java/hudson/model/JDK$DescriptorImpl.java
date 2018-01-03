/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import hudson.Extension;
import hudson.tools.JDKInstaller;
import hudson.tools.ToolDescriptor;
import hudson.util.FormValidation;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.jdk_tool.JDKs;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * @deprecated Use {@link JDKs#getDescriptor} and {@code ToolDescriptor<JDK>} to avoid needing to reference this class directly.
 */
@Extension @Symbol("jdk")
@Restricted(NoExternalUse.class)
public class JDK$DescriptorImpl extends ToolDescriptor<JDK> {

    private static final Logger LOGGER = Logger.getLogger(JDK$DescriptorImpl.class.getName());

    public JDK$DescriptorImpl() {
        super(JDK.class);
    }

    public String getDisplayName() {
        return "JDK"; // TODO I18N
    }

    public @Override JDK[] getInstallations() {
        return Jenkins.getInstance().getJDKs().toArray(new JDK[0]);
    }

    @SuppressWarnings("deprecation") // Ideally Jenkins#setJDKs would be @Restricted to all callers other than this plugin, but there is no such access restriction.
    public @Override void setInstallations(JDK... jdks) {
        try {
            Method m = Jenkins.class.getMethod("setJDKs", Collection.class);
            m.invoke(Jenkins.getInstance(), Arrays.asList(jdks));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            LOGGER.log(Level.SEVERE, "Unable to set JDK configurations.", e);
        }
    }

    @Override
    public List<JDKInstaller> getDefaultInstallers() {
        return Collections.singletonList(new JDKInstaller(null,false));
    }

    /**
     * Checks if the JAVA_HOME is a valid JAVA_HOME path.
     */
    @Override protected FormValidation checkHomeDirectory(File value) {
        File toolsJar = new File(value,"lib/tools.jar");
        File mac = new File(value,"lib/dt.jar");

        // JENKINS-25601: JDK 9+ no longer has tools.jar. Keep the existing dt.jar/tools.jar checks to be safe.
        File javac = new File(value, "bin/javac");
        File javacExe = new File(value, "bin/javac.exe");
        if(!toolsJar.exists() && !mac.exists() && !javac.exists() && !javacExe.exists())
            return FormValidation.error(Messages.Hudson_NotJDKDir(value));

        return FormValidation.ok();
    }

}
