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
package hudson.maven;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;

import javax.annotation.CheckForNull;

import hudson.util.InvocationInterceptor;
import hudson.util.ReflectionUtils;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

/**
 * Information about Mojo to be executed. This object provides
 * convenient access to various mojo information, so that {@link MavenReporter}
 * implementations are shielded to some extent from Maven internals.
 *
 * <p>
 * For each mojo to be executed, this object is created and passed to
 * {@link MavenReporter}.
 *
 * @author Kohsuke Kawaguchi
 * @see MavenReporter
 * @see MavenReportInfo
 */
public class MojoInfo {
    /**
     * Object from Maven that describes the Mojo to be executed.
     */
    public final MojoExecution mojoExecution;

    /**
     * PluginName of the plugin that contains this mojo.
     */
    public final PluginName pluginName;

    /**
     * Mojo object that carries out the actual execution.
     *
     * @deprecated as of 1.427
     *      Maven3 can no longer provide this information, so plugins cannot rely on this value being present.
     *      For the time being we are setting a dummy value to avoid NPE. Use {@link #configuration} to access
     *      configuration values, but otherwise the ability to inject values is lost and there's no viable
     *      alternative.
     */
    public final Mojo mojo;

    /**
     * Configuration of the mojo for the current execution.
     * This reflects the default values, as well as values configured from POM,
     * including inherited values.
     */
    public final PlexusConfiguration configuration;

    /**
     * Object that Maven uses to resolve variables like "${project}" to its
     * corresponding object.
     */
    public final ExpressionEvaluator expressionEvaluator;

    /**
     * Used to obtain a value from {@link PlexusConfiguration} as a typed object,
     * instead of String.
     */
    private final ConverterLookup converterLookup = new DefaultConverterLookup();

    private long startTime;

    public MojoInfo(MojoExecution mojoExecution, Mojo mojo, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator,
            long startTime) {
        // in Maven3 there's no easy way to get the Mojo instance that's being executed,
        // so we just can't pass it in.
        if (mojo==null) mojo = new Maven3ProvidesNoAccessToMojo();
        this.mojo = mojo;
        this.mojoExecution = mojoExecution;
        this.configuration = configuration;
        this.expressionEvaluator = expressionEvaluator;
        this.pluginName = new PluginName(mojoExecution.getMojoDescriptor().getPluginDescriptor());
        this.startTime = startTime;
    }

    public MojoInfo(ExecutionEvent event, long startTime) {
        this(event.getMojoExecution(), null,
                new XmlPlexusConfiguration( event.getMojoExecution().getConfiguration() ),
                new PluginParameterExpressionEvaluator( event.getSession(), event.getMojoExecution() ), startTime);
    }

    /**
     * Gets the goal name of the mojo to be executed,
     * such as "javadoc". This is local to the plugin name.
      */
    public String getGoal() {
        return mojoExecution.getMojoDescriptor().getGoal();
    }

    /**
     * Obtains the configuration value of the mojo.
     *
     * @param configName
     *      The name of the child element in the &lt;configuration> of mojo.
     * @param type
     *      The Java class of the configuration value. While every element
     *      can be read as {@link String}, often different types have a different
     *      conversion rules associated with it (for example, {@link File} would
     *      resolve relative path against POM base directory.)
     *  @param defaultValue
     *     The default value to return in case the mojo doesn't have such
     *     configuration value
     *
     * @return
     *      The configuration value either specified in POM, or inherited from
     *      parent POM, or default value if one is specified in mojo,
     *      or the defaultValue parameter if no such configuration value exists.
     *
     * @throws ComponentConfigurationException
     *      Not sure when exactly this is thrown, but it's probably when
     *      the configuration in POM is syntactically incorrect. 
     */
    public <T> T getConfigurationValue(String configName, Class<T> type, T defaultValue) throws ComponentConfigurationException {
        T value = getConfigurationValue(configName, type);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Obtains the configuration value of the mojo.
     *
     * @param configName
     *      The name of the child element in the &lt;configuration> of mojo.
     * @param type
     *      The Java class of the configuration value. While every element
     *      can be read as {@link String}, often different types have a different
     *      conversion rules associated with it (for example, {@link File} would
     *      resolve relative path against POM base directory.)
     *
     * @return
     *      The configuration value either specified in POM, or inherited from
     *      parent POM, or default value if one is specified in mojo,
     *      or null if no such configuration value exists.
     *
     * @throws ComponentConfigurationException
     *      Not sure when exactly this is thrown, but it's probably when
     *      the configuration in POM is syntactically incorrect. 
     */
    @CheckForNull public <T> T getConfigurationValue(String configName, Class<T> type) throws ComponentConfigurationException {
        PlexusConfiguration child = configuration.getChild(configName,false);
        if(child==null) return null;    // no such config
       
        final ClassLoader cl;
        PluginDescriptor pd = mojoExecution.getMojoDescriptor().getPluginDescriptor();
        
        // For maven2 builds getClassRealm returns a org.codehaus.classworlds.ClassRealm (instead of
        // org.codehaus.plexus.classworlds.realm.ClassRealm)
        // which doesn't extends ClassLoader !
        // So get this with reflection and access the nested classloader ("getClassLoader")
        Method method = ReflectionUtils.getPublicMethodNamed( pd.getClass(), "getClassRealm" );
       
        Object classRealm = ReflectionUtils.invokeMethod( method, pd );
        if ( classRealm instanceof ClassRealm) {
            ClassRealm cr = (ClassRealm) classRealm;
            cl = cr.getClassLoader();
        } else {
            cl = mojoExecution.getMojoDescriptor().getPluginDescriptor().getClassRealm();
        }
        ConfigurationConverter converter = converterLookup.lookupConverterForType(type);
        return type.cast(converter.fromConfiguration(converterLookup,child,type,
            // the implementation seems to expect the type of the bean for which the configuration is done
            // in this parameter, but we have no such type. So passing in a dummy
            Object.class,
            cl,
            expressionEvaluator));
    }

    /**
     * Returns true if this {@link MojoInfo} wraps the mojo of the given ID tuple.
     */
    public boolean is(String groupId, String artifactId, String mojoName) {
        return pluginName.matches(groupId,artifactId) && getGoal().equals(mojoName);
    }

    /**
     * Injects the specified value (designated by the specified field name) into the mojo,
     * and returns its old value.
     *
     * @throws NoSuchFieldException
     *      if the mojo doesn't have any field of the given name.
     * @since 1.232
     * @deprecated as of 1.427
     *      See the discussion in {@link #mojo}
     */
    @SuppressWarnings("unchecked")
    public <T> T inject(String name, T value) throws NoSuchFieldException {
        for(Class<?> c=mojo.getClass(); c!=Object.class; c=c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                Object oldValue = f.get(mojo);
                f.set(mojo,value);
                return (T)oldValue;
            } catch (NoSuchFieldException e) {
                continue;
            } catch (IllegalAccessException e) {
                // shouldn't happen because we made it accessible
                IllegalAccessError x = new IllegalAccessError(e.getMessage());
                x.initCause(e);
                throw x;
            }
        }

        throw new NoSuchFieldException(name);
    }

    /**
     * Intercept the invocation from the mojo to its injected component (designated by the given field name.)
     *
     * <p>
     * Often for a {@link MavenReporter} to really figure out what's going on in a build, you'd like
     * to intercept one of the components that Maven is injecting into the mojo, and inspect its parameter
     * and return values.
     *
     * <p>
     * This mehod provides a way to do this. You specify the name of the field in the Mojo class that receives
     * the injected component, then pass in {@link InvocationInterceptor}, which will in turn be invoked
     * for every invocation on that component.
     *
     * @throws NoSuchFieldException
     *      if the specified field is not found on the mojo class, or it is found but the type is not an interface.
     * @since 1.232
     * @deprecated as of 1.427
     *      See the discussion in {@link #mojo}
     */
    public void intercept(String fieldName, final InvocationInterceptor interceptor) throws NoSuchFieldException {
        for(Class<?> c=mojo.getClass(); c!=Object.class; c=c.getSuperclass()) {
            Field f;
            try {
                f = c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                continue;
            }

            f.setAccessible(true);
            Class<?> type = f.getType();
            if(!type.isInterface())
                throw new NoSuchFieldException(fieldName+" is of type "+type+" and it's not an interface");

            try {
                final Object oldObject = f.get(mojo);

                Object newObject = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        return interceptor.invoke(proxy,method,args,new InvocationHandler() {
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                return method.invoke(oldObject,args);
                            }
                        });
                    }
                });

                f.set(mojo,newObject);
            } catch (IllegalAccessException e) {
                // shouldn't happen because we made it accessible
                IllegalAccessError x = new IllegalAccessError(e.getMessage());
                x.initCause(e);
                throw x;
            }
        }
    }

    /**
     * Instance will be set to {@link MojoInfo#mojo} to avoid NPE in plugins.
     */
    public static class Maven3ProvidesNoAccessToMojo extends AbstractMojo {
        public void execute() throws MojoExecutionException, MojoFailureException {
            throw new UnsupportedOperationException();
        }
    }

    public long getStartTime() {
        return this.startTime;
    }
}
