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

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.LoggerManager;
import org.codehaus.plexus.configuration.PlexusConfigurationResourceException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.component.repository.exception.ComponentRepositoryException;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.factory.ComponentInstantiationException;
import org.codehaus.plexus.component.composition.CompositionException;
import org.codehaus.plexus.component.composition.UndefinedComponentComposerException;
import org.codehaus.plexus.component.discovery.ComponentDiscoveryListener;
import org.codehaus.classworlds.ClassRealm;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.io.Reader;
import java.io.File;

/**
 * {@link PlexusContainer} filter.
 * 
 * @author Kohsuke Kawaguchi
 */
class ContainerFilter implements PlexusContainer {
    private final PlexusContainer core;

    public ContainerFilter(PlexusContainer core) {
        this.core = core;
    }

    public Date getCreationDate() {
        return core.getCreationDate();
    }

    public boolean hasChildContainer(String name) {
        return core.hasChildContainer(name);
    }

    public void removeChildContainer(String name) {
        core.removeChildContainer(name);
    }

    public PlexusContainer getChildContainer(String name) {
        return core.getChildContainer(name);
    }

    public PlexusContainer createChildContainer(String name, List classpathJars, Map context) throws PlexusContainerException {
        return core.createChildContainer(name, classpathJars, context);
    }

    public PlexusContainer createChildContainer(String name, List classpathJars, Map context, List discoveryListeners) throws PlexusContainerException {
        return core.createChildContainer(name, classpathJars, context, discoveryListeners);
    }

    public Object lookup(String componentKey) throws ComponentLookupException {
        return core.lookup(componentKey);
    }

    public Object lookup(String role, String roleHint) throws ComponentLookupException {
        return core.lookup(role, roleHint);
    }

    public Map lookupMap(String role) throws ComponentLookupException {
        return core.lookupMap(role);
    }

    public List lookupList(String role) throws ComponentLookupException {
        return core.lookupList(role);
    }

    public ComponentDescriptor getComponentDescriptor(String componentKey) {
        return core.getComponentDescriptor(componentKey);
    }

    public Map getComponentDescriptorMap(String role) {
        return core.getComponentDescriptorMap(role);
    }

    public List getComponentDescriptorList(String role) {
        return core.getComponentDescriptorList(role);
    }

    public void addComponentDescriptor(ComponentDescriptor componentDescriptor) throws ComponentRepositoryException {
        core.addComponentDescriptor(componentDescriptor);
    }

    public void release(Object component) throws ComponentLifecycleException {
        core.release(component);
    }

    public void releaseAll(Map components) throws ComponentLifecycleException {
        core.releaseAll(components);
    }

    public void releaseAll(List components) throws ComponentLifecycleException {
        core.releaseAll(components);
    }

    public boolean hasComponent(String componentKey) {
        return core.hasComponent(componentKey);
    }

    public boolean hasComponent(String role, String roleHint) {
        return core.hasComponent(role, roleHint);
    }

    public void suspend(Object component) throws ComponentLifecycleException {
        core.suspend(component);
    }

    public void resume(Object component) throws ComponentLifecycleException {
        core.resume(component);
    }

    public void initialize() throws PlexusContainerException {
        core.initialize();
    }

    public boolean isInitialized() {
        return core.isInitialized();
    }

    public void start() throws PlexusContainerException {
        core.start();
    }

    public boolean isStarted() {
        return core.isStarted();
    }

    public void dispose() {
        core.dispose();
    }

    public Context getContext() {
        return core.getContext();
    }

    public void setParentPlexusContainer(PlexusContainer parentContainer) {
        core.setParentPlexusContainer(parentContainer);
    }

    public void addContextValue(Object key, Object value) {
        core.addContextValue(key, value);
    }

    public void setConfigurationResource(Reader configuration) throws PlexusConfigurationResourceException {
        core.setConfigurationResource(configuration);
    }

    public Logger getLogger() {
        return core.getLogger();
    }

    public Object createComponentInstance(ComponentDescriptor componentDescriptor) throws ComponentInstantiationException, ComponentLifecycleException {
        return core.createComponentInstance(componentDescriptor);
    }

    public void composeComponent(Object component, ComponentDescriptor componentDescriptor) throws CompositionException, UndefinedComponentComposerException {
        core.composeComponent(component, componentDescriptor);
    }

    public void registerComponentDiscoveryListener(ComponentDiscoveryListener listener) {
        core.registerComponentDiscoveryListener(listener);
    }

    public void removeComponentDiscoveryListener(ComponentDiscoveryListener listener) {
        core.removeComponentDiscoveryListener(listener);
    }

    public void addJarRepository(File repository) {
        core.addJarRepository(repository);
    }

    public void addJarResource(File resource) throws PlexusContainerException {
        core.addJarResource(resource);
    }

    public ClassRealm getContainerRealm() {
        return core.getContainerRealm();
    }

    public ClassRealm getComponentRealm(String componentKey) {
        return core.getComponentRealm(componentKey);
    }

    public void setLoggerManager(LoggerManager loggerManager) {
        core.setLoggerManager(loggerManager);
    }

    public LoggerManager getLoggerManager() {
        return core.getLoggerManager();
    }
}
