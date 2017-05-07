/*
 * The MIT License
 *
 * Copyright (c) 2017 Oleg Nenashev.
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
package jenkins.slaves;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Computer;
import hudson.model.Slave;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Allows providing custom data for JNLP agents.
 * This extension point is mainly created for the Agent Remoting Support plugin.
 * It can be also used in other plugins in order to get custom behavior.
 * @author Oleg Nenashev
 * @since TODO
 */
public abstract class JnlpDataProvider implements ExtensionPoint {
    
    /**
     * Provides custom JnlpJar file if required.
     * @param fileName File name (e.g. slave.jar)
     * @return Custom JAR file or {@code null} if it cannot be provided.
     */
    @CheckForNull
    public abstract Slave.JnlpJar provideJarFile(@Nonnull String fileName);
    
    /**
     * Provides JNLP file as {@link HttpResponse}.
     * @param computer Computer, for which the file should be provided
     * @param req Request. Can be used to retrieve additional metadata like parameters.
     * @return JNLP File
     */
    @CheckForNull
    public abstract HttpResponse provideJnlpFile(@Nonnull Computer computer, @CheckForNull StaplerRequest req);
    
    public static ExtensionList<JnlpDataProvider> all() {
        return ExtensionList.lookup(JnlpDataProvider.class);
    }
    
    /**
     * Finds JNLP JAR by name.
     * @param fileName File name
     * @return JNLP Jar contributed by an extension point or a built-in {@link Slave.JnlpJar} as a fallback.
     */
    @Nonnull
    public static Slave.JnlpJar getJarFile(@Nonnull String fileName) {
        for (JnlpDataProvider provider : all()) {
            Slave.JnlpJar jnlpJar = provider.provideJarFile(fileName);
            if (jnlpJar != null) {
                return jnlpJar;
            }
        }
        
        // Fallback to the default provider in the core
        return new Slave.JnlpJar(fileName);
    }
    
    /**
     * Finds JNLP file by name.
     * @param computer Computer, for which the file should be provided
     * @param req Request with additional data like parameters.
     *            May be {@code null} if it is an API call.
     * @return Response with the retrieved file.
     */
    @CheckForNull
    public static HttpResponse getJnlpFile(@Nonnull Computer computer, @CheckForNull StaplerRequest req) {
        for (JnlpDataProvider provider : all()) {
            HttpResponse response = provider.provideJnlpFile(computer, req);
            if (response != null) {
                return response;
            }
        }
        
        // Fallback to the default provider in the core
        return null;
    }
}
