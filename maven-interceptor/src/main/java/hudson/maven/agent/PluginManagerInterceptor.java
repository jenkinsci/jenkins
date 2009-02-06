/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.maven.agent;

import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.DefaultPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Description in META-INF/plexus/components.xml makes it possible to use this instead of the default
 * plugin manager.
 *
 * @author Kohsuke Kawaguchi
 */
public class PluginManagerInterceptor extends DefaultPluginManager {
    /**
     * {@link PluginManagerListener} that receives events.
     * There's no way external code can connect to a running instance of
     * {@link PluginManagerInterceptor}, so this cannot be made instance fields. 
     */
    private static PluginManagerListener listener;

    /**
     * {@link ComponentConfigurator} filter to intercept the mojo configuration.
     */
    private ComponentConfiguratorFilter configuratorFilter;

    public static void setListener(PluginManagerListener _listener) {
        listener = _listener;
    }

    @Override
    public void initialize() {
        super.initialize();
        container = new ContainerFilter(container) {
            /**
             * {@link DefaultPluginManager} uses it to load plugins and their configurators.
             *
             * @param name
             *      groupId+':'+artifactId of the plugin.
             */
            public PlexusContainer getChildContainer(String name) {
                PlexusContainer child = super.getChildContainer(name);
                if(child==null) return null;
                return new ContainerFilter(child) {
                    public Object lookup(String componentKey) throws ComponentLookupException {
                        return wrap(super.lookup(componentKey), componentKey);
                    }

                    public Object lookup(String role, String roleHint) throws ComponentLookupException {
                        return wrap(super.lookup(role,roleHint), role);
                    }

                    public void release(Object component) throws ComponentLifecycleException {
                        if(component==configuratorFilter)
                            super.release(configuratorFilter.core);
                        else
                            super.release(component);
                    }

                    private Object wrap(Object c, String componentKey) {
                        if(configuratorFilter==null)
                            return c; // not activated
                        if(c!=null && componentKey.equals(ComponentConfigurator.ROLE)) {
                            if(configuratorFilter.core!=null)
                                throw new IllegalStateException("ComponentConfigurationFilter being reused. " +
                                    "This is a bug in Hudson. Please report that to the development team.");
                            configuratorFilter.core = (ComponentConfigurator)c;
                            c = configuratorFilter;
                        }
                        return c;
                    }
                };
            }
        };
    }

    /**
     * Intercepts the {@link Mojo} configuration and grabs some key Maven objects that are used for configuration,
     * then call {@link #pre(Object, PlexusConfiguration, ExpressionEvaluator)} to provide an opportunity
     * to alter the configuration.
     */
    private abstract class MojoIntercepter extends ComponentConfiguratorFilter {
        // these are the key objects involved in configuring a mojo
        PlexusConfiguration config;
        ExpressionEvaluator eval;
        Mojo mojo;

        MojoIntercepter() {
            super(null);
            // it is the caller's responsibility to set 'configuratorFilter' to null when the interception is over.
            configuratorFilter = this;
        }

        @Override
        public void configureComponent(Object component, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator, ClassRealm containerRealm, ConfigurationListener configListener) throws ComponentConfigurationException {
            try {
                this.config = configuration;
                this.eval = expressionEvaluator;
                this.mojo = (Mojo)component;
                pre(component, configuration, expressionEvaluator);
                super.configureComponent(component, configuration, expressionEvaluator, containerRealm, configListener);
            } catch (IOException e) {
                throw new ComponentConfigurationException(e);
            } catch (InterruptedException e) {
                // orderly abort
                throw new AbortException("Execution aborted",e);
            }
        }

        protected abstract void pre(Object component, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator) throws IOException, InterruptedException;
    }

    @Override
    public void executeMojo(final MavenProject project, final MojoExecution mojoExecution, MavenSession session) throws ArtifactResolutionException, MojoExecutionException, MojoFailureException, ArtifactNotFoundException, InvalidDependencyVersionException, PluginManagerException, PluginConfigurationException {
        class MojoIntercepterImpl extends MojoIntercepter {
            @Override
            protected void pre(Object component, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator) throws IOException, InterruptedException {
                if(listener!=null)
                    // this lets preExecute a chance to modify the mojo configuration
                    listener.preExecute(project,mojoExecution, (Mojo)component, configuration,expressionEvaluator);
            }

            void callPost(Exception exception) throws IOException, InterruptedException {
                if(listener!=null)
                    listener.postExecute(project,mojoExecution,mojo,config,eval,exception);
            }
        }

        // prepare interception of ComponentConfigurator, so that we can get the final PlexusConfiguration object
        // representing the configuration before Mojo object is filled with that.
        MojoIntercepterImpl interceptor = new MojoIntercepterImpl();

        try {
            try {
                // inside the executeMojo but before the mojo actually gets executed,
                // we should be able to trap the mojo configuration.
                super.executeMojo(project, mojoExecution, session);
                interceptor.callPost(null);
            } catch (MojoExecutionException e) {
                interceptor.callPost(e);
                throw e;
            } catch (MojoFailureException e) {
                interceptor.callPost(e);
                throw e;
            }
        } catch (InterruptedException e) {
            // orderly abort
            throw new AbortException("Execution aborted",e);
        } catch (IOException e) {
            throw new PluginManagerException(e.getMessage(),e);
        } finally {
            configuratorFilter = null;
        }
    }

    /**
     * Intercepts the creation of {@link MavenReport}, to intercept
     * the execution of it. This is used to discover the execution
     * of certain reporting.
     */
    @Override
    public MavenReport getReport(MavenProject project, final MojoExecution mojoExecution, MavenSession session) throws ArtifactNotFoundException, PluginConfigurationException, PluginManagerException, ArtifactResolutionException {
        // intercept the MavenReport object creation. 
        final MojoIntercepter interceptor = new MojoIntercepter() {
            protected void pre(Object component, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator) throws IOException, InterruptedException {
            }
        };

        MavenReport r;
        try {
            r = super.getReport(project, mojoExecution, session);
        } finally {
            configuratorFilter = null;
        }
        if(r==null)     return null;
        
        r = new ComponentInterceptor<MavenReport>() {
            /**
             * Intercepts the execution of methods on {@link MavenReport}.
             */
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if(method.getName().equals("generate")) {
                    Object r = super.invoke(proxy, method, args);
                    // on successul execution of the generate method, raise an event
                    try {
                        listener.onReportGenerated(delegate,mojoExecution,interceptor.config,interceptor.eval);
                    } catch (InterruptedException e) {
                        // orderly abort
                        throw new AbortException("Execution aborted",e);
                    } catch (IOException e) {
                        throw new MavenReportException(e.getMessage(),e);
                    }
                    return r;
                }

                // delegate by default
                return super.invoke(proxy, method, args);
            }
        }.wrap(r);
        return r;
    }
}
