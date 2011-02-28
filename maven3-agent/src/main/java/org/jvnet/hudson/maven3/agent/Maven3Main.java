/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Olivier Lamy
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
package org.jvnet.hudson.maven3.agent;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.codehaus.plexus.classworlds.launcher.Launcher;
import org.codehaus.plexus.classworlds.realm.ClassRealm;



/**
 * Entry point for launching Maven 3 and Hudson remoting in the same VM, in the
 * classloader layout that Maven expects.
 * 
 * <p>
 * The actual Maven execution will be started by the program sent through
 * remoting.
 * </p>
 * 
 * @author Kohsuke Kawaguchi
 * @author Olivier Lamy
 */
public class Maven3Main {
	/**
	 * Used to pass the classworld instance to the code running inside the
	 * remoting system.
	 */
	private static Launcher launcher;

	public static void main(String[] args) throws Exception {
		main(new File(args[0]), new File(args[1]),new File(args[2]),
				Integer.parseInt(args[3]));		
	}

	/**
	 * 
	 * @param m2Home
	 *            Maven2 installation. This is where we find Maven jars that
	 *            we'll run.
	 * @param remotingJar
	 *            Hudson's remoting.jar that we'll load.
	 * @param interceptorJar
	 *            maven-listener.jar that we'll load.
	 * @param tcpPort
	 *            TCP socket that the launching Hudson will be listening to.
	 *            This is used for the remoting communication.
	 * @param projectBuildLaunch
	 *            launch the projectBuilder and not a mavenExecution            
	 */
	public static void main(File m2Home, File remotingJar, File interceptorJar,
			int tcpPort) throws Exception {
		// Unix master with Windows slave ends up passing path in Unix format,
		// so convert it to Windows format now so that no one chokes with the
		// path format later.
		try {
			m2Home = m2Home.getCanonicalFile();
		} catch (IOException e) {
			// ignore. We'll check the error later if m2Home exists anyway
		}

		if (!m2Home.exists()) {
			System.err.println("No such directory exists: " + m2Home);
			System.exit(1);
		}

		versionCheck();
			
		// expose variables used in the classworlds configuration
		System.setProperty("maven.home", m2Home.getPath());
		System.setProperty("maven3.interceptor", (interceptorJar != null ? interceptorJar
				: interceptorJar).getPath());

		// load the default realms
		launcher = new Launcher();
		launcher.setSystemClassLoader(Maven3Main.class.getClassLoader());
		launcher.configure(getClassWorldsConfStream());
		

		// create a realm for loading remoting subsystem.
		// this needs to be able to see maven.
		ClassRealm remoting = launcher.getWorld().newRealm( "hudson-remoting", launcher.getSystemClassLoader() );
		remoting.setParentRealm(launcher.getWorld().getRealm("plexus.core"));
		remoting.addURL(remotingJar.toURI().toURL());
		
		final Socket s = new Socket((String) null, tcpPort);
		
		Class remotingLauncher = remoting.loadClass("hudson.remoting.Launcher");
		remotingLauncher.getMethod("main",
				new Class[] { InputStream.class, OutputStream.class }).invoke(
				null,
				new Object[] {
						// do partial close, since socket.getInputStream and
						// getOutputStream doesn't do it by
						new BufferedInputStream(new FilterInputStream(s
								.getInputStream()) {
							public void close() throws IOException {
								s.shutdownInput();
							}
						}),
						new BufferedOutputStream(new RealFilterOutputStream(s
								.getOutputStream()) {
							public void close() throws IOException {
								s.shutdownOutput();
							}
						}) });
		System.exit(0);
	}

	/**
	 * Called by the code in remoting to launch.
	 */
	public static int launch(String[] args) throws Exception {
		
		try {
			launcher.launch(args);
		} catch (Throwable e)
		{
		    e.printStackTrace();
		    throw new Exception( e );
		} 
		return launcher.getExitCode();
	}

    private static InputStream getClassWorldsConfStream() throws FileNotFoundException {
        String classWorldsConfLocation = System.getProperty("classworlds.conf");
        if (classWorldsConfLocation == null || classWorldsConfLocation.trim().length() == 0) {
            classWorldsConfLocation = System.getenv("classworlds.conf");
            if (classWorldsConfLocation == null || classWorldsConfLocation.trim().length() == 0) {
                return Maven3Main.class.getResourceAsStream("classworlds.conf");
            }
        }
        return new FileInputStream(new File(classWorldsConfLocation));
    }
	
    /**
     * Makes sure that this is Java5 or later.
     */
    private static void versionCheck() {
        String v = System.getProperty("java.class.version");
        if (v != null) {
            try {
                if (Float.parseFloat(v) < 49.0) {
                    System.err
                            .println("Native maven support requires Java 1.5 or later, but this Maven is using "
                                    + System.getProperty("java.home"));
                    System.err.println("Please use the freestyle project.");
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                // couldn't check.
            }
        }
    }	
	
}