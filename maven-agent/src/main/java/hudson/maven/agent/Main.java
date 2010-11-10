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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.tools.ant.AntClassLoader;
import org.codehaus.classworlds.ClassWorldAdapter;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.launcher.ConfigurationException;
import org.codehaus.plexus.classworlds.launcher.Launcher;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;


/**
 * Entry point for launching Maven and Hudson remoting in the same VM,
 * in the classloader layout that Maven expects.
 *
 * <p>
 * The actual Maven execution will be started by the program sent
 * through remoting. 
 *
 * @author Kohsuke Kawaguchi
 */
public class Main {
    /**
     * Used to pass the classworld instance to the code running inside the remoting system.
     */
    private static Launcher launcher;

    public static void main(String[] args) throws Exception {
        main(new File(args[0]),new File(args[1]),new File(args[2]),Integer.parseInt(args[3]),
                args.length==4?null:new File(args[4]));
    }

    /**
     *
     * @param m2Home
     *      Maven2 installation. This is where we find Maven jars that we'll run.
     * @param remotingJar
     *      Hudson's remoting.jar that we'll load.
     * @param interceptorJar
     *      maven-interceptor.jar that we'll load.
     * @param tcpPort
     *      TCP socket that the launching Hudson will be listening to.
     *      This is used for the remoting communication.
     * @param interceptorOverrideJar
     *      Possibly null override jar to be placed in front of maven-interceptor.jar
     */
    public static void main(File m2Home, File remotingJar, File interceptorJar, int tcpPort, File interceptorOverrideJar) throws Exception {
        // Unix master with Windows slave ends up passing path in Unix format,
        // so convert it to Windows format now so that no one chokes with the path format later.
        try {
            m2Home = m2Home.getCanonicalFile();
        } catch (IOException e) {
            // ignore. We'll check the error later if m2Home exists anyway
        }

        if(!m2Home.exists()) {
            System.err.println("No such directory exists: "+m2Home);
            System.exit(1);
        }

        versionCheck();

        // expose variables used in the classworlds configuration
        System.setProperty("maven.home",m2Home.getPath());
        System.setProperty("maven.interceptor",interceptorJar.getPath());
        System.setProperty("maven.interceptor.override",
                // I don't know how classworlds react to undefined variable, so 
                (interceptorOverrideJar!=null?interceptorOverrideJar:interceptorJar).getPath());

        // FIXME change stuff for 206 earlier !
        
        boolean is206OrLater = !new File(m2Home,"core").exists();
               
        
        // load the default realms
        launcher = new Launcher();
        //launcher.setSystemClassLoader(Main.class.getClassLoader());
        
        configureLauncher( m2Home, remotingJar, interceptorJar, interceptorOverrideJar, is206OrLater );
        
        System.out.println( "realms size " + launcher.getWorld().getRealms().size() );
        for (Iterator iter = launcher.getWorld().getRealms().iterator();iter.hasNext();)
        {
            ClassRealm realm = (ClassRealm) iter.next();
            System.out.println( "realm " + realm + " urls " +  Arrays.asList( realm.getURLs() ) );
        }
        
        // have it eventually delegate to this class so that this can be visible
        // create a realm for loading remoting subsystem.
        // this needs to be able to see maven.
        
        System.out.println( "Main classLoader " + Main.class.getClassLoader() );
        ClassRealm remoting = launcher.getWorld().newRealm( "hudson-remoting", launcher.getWorld().getClassRealm( "plexus.core" ) );
        remoting.importFrom( "plexus.core.maven", "org.apache.maven" );
        //remoting.setParentClassLoader( launcher.getWorld().getClassRealm( "plexus.core.maven" ) );
        remoting.addURL(remotingJar.toURI().toURL());        

        final Socket s = new Socket((String)null,tcpPort);

        
        Class remotingLauncher = remoting.loadClass("hudson.remoting.Launcher");
                
        Method mainMethod = remotingLauncher.getMethod("main",new Class[]{InputStream.class,OutputStream.class});
        
        mainMethod.invoke(null,
                new Object[]{
                        // do partial close, since socket.getInputStream and getOutputStream doesn't do it by
                        new BufferedInputStream(new FilterInputStream(s.getInputStream()) {
                            public void close() throws IOException {
                                s.shutdownInput();
                            }
                        }),
                        new BufferedOutputStream(new RealFilterOutputStream(s.getOutputStream()) {
                            public void close() throws IOException {
                                s.shutdownOutput();
                            }
                        })
                });
        System.out.println(" remoting classLoader " + remoting.toString() );
        System.exit(0);
    }

    /**
     * Makes sure that this is Java5 or later.
     */
    private static void versionCheck() {
        String v = System.getProperty("java.class.version");
        if(v!=null) {
            try {
                if(Float.parseFloat(v)<49.0) {
                    System.err.println("Native maven support requires Java 1.5 or later, but this Maven is using "+System.getProperty("java.home"));
                    System.err.println("Please use the freestyle project.");
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                // couldn't check.
            }
        }
    }
    
    /**
     * old one
     * @throws ConfigurationException 
     * @throws IOException 
     * @throws NoSuchRealmException 
     * @throws DuplicateRealmException 
     */
    private static void configureLauncher(File m2Home, File remotingJar, File interceptorJar, File interceptorOverrideJar, boolean is206OrLater)
        throws DuplicateRealmException, NoSuchRealmException, IOException, ConfigurationException {
        
        launcher.configure(Main.class.getResourceAsStream(is206OrLater?"classworlds-2.0.6.conf":"classworlds.conf"));        
    }

    /**
     * with various classLoader hierarchy stuff
     * @throws MalformedURLException 
     * @throws DuplicateRealmException 
     */
    private static void configureLauncher2(File m2Home, File remotingJar, File interceptorJar, File interceptorOverrideJar, boolean is206OrLater) 
        throws MalformedURLException, DuplicateRealmException {
        ClassWorld world = new ClassWorld();
        launcher.setWorld( world );
        ClassRealm plexusCore = world.newRealm( "plexus.core" );
        plexusCore.setParentClassLoader( Main.class.getClassLoader() );
        File mavenCore = new File(m2Home, is206OrLater ? "boot" : "core");
        
        String[] coreJars = mavenCore.list( new FilenameFilter()
        {
            
            public boolean accept( File dir, String name )
            {
                return name.endsWith( ".jar" );
            }
        });
        
        for (int i = 0,size = coreJars.length;i<size;i++)
        {
            plexusCore.addURL( new File(coreJars[i]).toURI().toURL() );
            System.out.println("adding jar to plexusCore " + coreJars[i] );
        }
        
        
        ChildFistClassCloader childFistClassCloader = new ChildFistClassCloader( plexusCore );
        
        File mavenLib = new File(m2Home, "lib");
        
        String[] libJars = mavenLib.list( new FilenameFilter()
        {
            
            public boolean accept( File dir, String name )
            {
                return name.endsWith( ".jar" );
            }
        });
        
        for (int i = 0,size = libJars.length;i<size;i++)
        {
            childFistClassCloader.addPathComponent( new File(libJars[i]) );
            System.out.println("adding jar " + libJars[i] );
        }
        
        childFistClassCloader.addPathComponent( interceptorJar );
        
        if (interceptorOverrideJar!=null)
        {
            childFistClassCloader.addPathComponent( interceptorOverrideJar );
        }
        
        ClassRealm plexusCoreMaven = world.newRealm(  "plexus.core.maven" );
        plexusCore.setParentClassLoader( childFistClassCloader );
        for (int i = 0,size = libJars.length;i<size;i++)
        {
            plexusCoreMaven.addURL( new File(libJars[i]).toURI().toURL() );
        }        
        plexusCoreMaven.addURL( interceptorJar.toURI().toURL() );
        if (interceptorOverrideJar!=null)
        {
            plexusCoreMaven.addURL( interceptorOverrideJar.toURI().toURL() );
        }        
                
    }    
    
    /**
     * Called by the code in remoting to launch.
     * @throws org.codehaus.classworlds.DuplicateRealmException 
     * @throws IllegalArgumentException 
     */
    public static int launch(String[] args) 
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException, IOException, IllegalArgumentException {
        
        ClassWorld world = launcher.getWorld();

        Set builtinRealms = new HashSet(world.getRealms());
        URLClassLoader orig = (URLClassLoader) Thread.currentThread().getContextClassLoader();
        System.out.println("orig " + orig.toString());
      
        
        try {
            launcher.setAppMain( "org.apache.maven.cli.MavenCli", "plexus.core.maven" );
            ClassRealm newCl = launcher.getMainRealm();
            Thread.currentThread().setContextClassLoader( newCl );
            Method mainMethod = launcher.getMainClass().getMethod( "main", new Class[]{String[].class, org.codehaus.classworlds.ClassWorld.class} );
            //launcher.launch(args);
            
            mainMethod.invoke( null,new Object[]{args,ClassWorldAdapter.getInstance( launcher.getWorld() )} );
        //} catch (org.codehaus.classworlds.DuplicateRealmException e) {
        //    throw new RuntimeException( e.getMessage(), e);
        } catch (NoSuchRealmException e) {
            throw new RuntimeException( e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader( orig );
            // delete all realms created by Maven
            // this is because Maven creates a child realm for each plugin it loads,
            // and the realm id doesn't include the version.
            // so unless we discard all the realms multiple invocations
            // that use different versions of the same plugin will fail to work correctly.
            Set all = new HashSet(world.getRealms());
            all.removeAll(builtinRealms);
            for (Iterator itr = all.iterator(); itr.hasNext();) {
                ClassRealm cr = (ClassRealm) itr.next();
                try
                {
                    world.disposeRealm(cr.getId());
                }
                catch ( NoSuchRealmException e )
                {
                    throw new RuntimeException( e.getMessage(), e);
                }
            }
        }
        return launcher.getExitCode();
    }
    
    private static org.codehaus.classworlds.ClassWorld convertType(ClassWorld classWorld) 
        throws org.codehaus.classworlds.DuplicateRealmException
    {
        org.codehaus.classworlds.ClassWorld old = new org.codehaus.classworlds.ClassWorld();
        for (Iterator ite = classWorld.getRealms().iterator();ite.hasNext();)
        {
            ClassRealm realm = (ClassRealm) ite.next();
            old.newRealm( realm.getId(), realm );
        }
        return old;
    }
    
    static class ChildFistClassCloader extends AntClassLoader
    {
        ChildFistClassCloader (ClassLoader parent)
        {
            super( parent, false );
        }
        

        protected Enumeration findResources( String name )
            throws IOException
        {
            Enumeration enu = super.findResources( name );
            return enu;
        }

        public URL getResource( String name )
        {
            URL url = super.getResource( name );
            return url;
        }

        public InputStream getResourceAsStream( String name )
        {
            InputStream is = super.getResourceAsStream( name );
            return is;
        }


        public Class findClass( String name )
            throws ClassNotFoundException
        {
            return super.findClass( name );
        }


        protected synchronized Class loadClass( String arg0, boolean arg1 )
            throws ClassNotFoundException
        {
            return super.loadClass( arg0, arg1 );
        }   
    }
}