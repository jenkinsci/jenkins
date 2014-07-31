/**
 * 
 */
package hudson.maven;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import hudson.model.TaskListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.transfer.TransferListener;

/**
 * @author Olivier Lamy
 */
public class MavenEmbedderRequest
{
    private TaskListener listener;

    private File mavenHome;

    private String profiles;

    private Properties systemProperties;

    private String privateRepository;

    private File alternateSettings;

    private TransferListener transferListener;
    
    /**
     * The classloader used to create Maven embedder.
     *
     * This needs to be able to see all the plexus components for core Maven stuff.
     *
     * @since 1.393
     */
    private ClassLoader classLoader = getDefaultMavenClassLoader();

    /**
     * will processPlugins during project reading
     * @since 1.393
     */
    private boolean processPlugins;
    
    /**
     * will resolve dependencies during project reading
     * @since 1.393
     */    
    private boolean resolveDependencies;    

    /**
     * level of validation when reading pom (ie model building request)
     * default value : {@link ModelBuildingRequest#VALIDATION_LEVEL_MAVEN_2_0} etc...
     * @since 1.393
     */    
    private int validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_2_0;
    
    /**
     * @since 1.393
     */
    private WorkspaceReader workspaceReader;

    /**
     * @since 1.426
     */
    private File globalSettings;

    /**
     * @since 1.461
     */
    private boolean updateSnapshots;

    /**
     * @param listener
     *      This is where the log messages from Maven will be recorded.
     * @param mavenHome
     *      Directory of the Maven installation. We read {@code conf/settings.xml}
     *      from here. Can be null.
     * @param profiles
     *      Profiles to activate/deactivate. Can be null.
     * @param systemProperties
     *      The system properties that the embedded Maven sees. See {@link MavenEmbedder#setSystemProperties(Properties)}.
     * @param privateRepository
     *      Optional private repository to use as the local repository.
     * @param alternateSettings
     *      Optional alternate settings.xml file.
     */
    public MavenEmbedderRequest( TaskListener listener, File mavenHome, String profiles, Properties systemProperties,
                                 String privateRepository, File alternateSettings ) {
        this.listener = listener;
        this.mavenHome = mavenHome;
        this.profiles = profiles;
        this.systemProperties = systemProperties;
        this.privateRepository = privateRepository;
        this.alternateSettings = alternateSettings;
    }

    public TaskListener getListener() {
        return listener;
    }

    public MavenEmbedderRequest setListener( TaskListener listener ) {
        this.listener = listener;
        return this;
    }

    public File getMavenHome() {
        return mavenHome;
    }

    public MavenEmbedderRequest setMavenHome( File mavenHome ) {
        this.mavenHome = mavenHome;
        return this;
    }

    public String getProfiles() {
        return profiles;
    }

    public MavenEmbedderRequest setProfiles( String profiles ) {
        this.profiles = profiles;
        return this;
    }

    public Properties getSystemProperties() {
        return systemProperties;
    }

    public MavenEmbedderRequest setSystemProperties( Properties systemProperties ) {
        this.systemProperties = systemProperties;
        return this;
    }

    public String getPrivateRepository() {
        return privateRepository;
    }

    public MavenEmbedderRequest setPrivateRepository( String privateRepository ) {
        this.privateRepository = privateRepository;
        return this;
    }

    public File getAlternateSettings() {
        return alternateSettings;
    }

    /**
     * Overrides the user settings (by default we look at ~/.m2/settings.xml)
     */
    public MavenEmbedderRequest setAlternateSettings( File alternateSettings ) {
        this.alternateSettings = alternateSettings;
        return this;
    }

    public TransferListener getTransferListener() {
        return transferListener;
    }

    public MavenEmbedderRequest setTransferListener( TransferListener transferListener ) {
        this.transferListener = transferListener;
        return this;
    }

    /**
     * Default value of {@link #getClassLoader()}
     * @since 1.519
     */
    public static ClassLoader getDefaultMavenClassLoader() {
        return new MaskingClassLoader( MavenUtil.class.getClassLoader() );
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public MavenEmbedderRequest setClassLoader( ClassLoader classLoader ) {
        this.classLoader = classLoader;
        return this;
    }

    public boolean isProcessPlugins() {
        return processPlugins;
    }

    public MavenEmbedderRequest setProcessPlugins( boolean processPlugins ) {
        this.processPlugins = processPlugins;
        return this;
    }

    public boolean isResolveDependencies() {
        return resolveDependencies;
    }

    public MavenEmbedderRequest setResolveDependencies( boolean resolveDependencies ) {
        this.resolveDependencies = resolveDependencies;
        return this;
    }

    public int getValidationLevel() {
        return validationLevel;
    }

    /**
     * Controls the level of error checks done while parsing POM.
     *
     * @see ModelBuildingRequest#VALIDATION_LEVEL_MAVEN_3_0
     */
    public MavenEmbedderRequest setValidationLevel( int validationLevel ) {
        this.validationLevel = validationLevel;
        return this;
    }

    public WorkspaceReader getWorkspaceReader() {
        return workspaceReader;
    }

    public void setWorkspaceReader( WorkspaceReader workspaceReader ) {
        this.workspaceReader = workspaceReader;
    }

    public File getGlobalSettings() {
        return globalSettings;
    }

    public MavenEmbedderRequest setGlobalSettings( File globalSettings ) {
        this.globalSettings = globalSettings;
        return this;
    }

    public MavenEmbedderRequest setUpdateSnapshots(boolean updateSnapshots) {
      this.updateSnapshots = updateSnapshots;
      return this;
    }
    
    public boolean isUpdateSnapshots() {
      return updateSnapshots;
    }


    /**
     * When we run in Jetty during development, embedded Maven will end up
     * seeing some of the Maven class visible through Jetty, and this confuses it.
     *
     * <p>
     * Specifically, embedded Maven will find all the component descriptors
     * visible through Jetty, yet when it comes to loading classes, classworlds
     * still load classes from local realms created inside embedder.
     *
     * <p>
     * This classloader prevents this issue by hiding the component descriptor
     * visible through Jetty.
     */
    private static final class MaskingClassLoader extends ClassLoader {

        public MaskingClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Enumeration<URL> getResources(String name) throws IOException {
            final Enumeration<URL> e = super.getResources(name);
            return new Enumeration<URL>() {
                URL next;

                public boolean hasMoreElements() {
                    fetch();
                    return next!=null;
                }

                public URL nextElement() {
                    fetch();
                    URL r = next;
                    next = null;
                    return r;
                }

                private void fetch() {
                    while(next==null && e.hasMoreElements()) {
                        next = e.nextElement();
                        if(shouldBeIgnored(next))
                            next = null;
                    }
                }

                private boolean shouldBeIgnored(URL url) {
                    String s = url.toExternalForm();
                    if(s.contains("maven-plugin-tools-api"))
                        return true;
                    // because RemoteClassLoader mangles the path, we can't check for plexus/components.xml,
                    // which would have otherwise made the test cheaper.
                    if(s.endsWith("components.xml")) {
                        BufferedReader r=null;
                        try {
                            // is this designated for interception purpose? If so, don't load them in the MavenEmbedder
                            // earlier I tried to use a marker file in the same directory, but that won't work
                            r = new BufferedReader(new InputStreamReader(url.openStream()));
                            for (int i=0; i<2; i++) {
                                String l = r.readLine();
                                if(l!=null && l.contains("MAVEN-INTERCEPTION-TO-BE-MASKED"))
                                    return true;
                            }
                        } catch (IOException _) {
                            // let whoever requesting this resource re-discover an error and report it
                        } finally {
                            IOUtils.closeQuietly(r);
                        }
                    }
                    return false;
                }
            };
        }
    }
}
