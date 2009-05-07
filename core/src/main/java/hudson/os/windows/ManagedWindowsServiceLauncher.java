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
package hudson.os.windows;

import hudson.lifecycle.WindowsSlaveInstaller;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.Secret;
import hudson.util.jna.DotNet;
import hudson.remoting.Channel;
import hudson.remoting.SocketInputStream;
import hudson.remoting.SocketOutputStream;
import hudson.remoting.Channel.Listener;
import hudson.Extension;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbException;
import jcifs.smb.NtlmPasswordAuthentication;
import org.apache.commons.io.IOUtils;
import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.common.JIDefaultAuthInfoImpl;
import org.jinterop.dcom.core.JISession;
import org.kohsuke.stapler.DataBoundConstructor;
import org.jvnet.hudson.wmi.WMI;
import org.jvnet.hudson.wmi.SWbemServices;
import org.jvnet.hudson.wmi.Win32Service;
import static org.jvnet.hudson.wmi.Win32Service.Win32OwnProcess;
import org.dom4j.io.SAXReader;
import org.dom4j.Document;
import org.dom4j.DocumentException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.PrintStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.UnknownHostException;
import java.net.Socket;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Windows slave installed/managed as a service entirely remotely
 *
 * @author Kohsuke Kawaguchi
 */
public class ManagedWindowsServiceLauncher extends ComputerLauncher {
    /**
     * "[DOMAIN\\]USERNAME" to follow the Windows convention.
     */
    public final String userName;
    
    public final Secret password;

    @DataBoundConstructor
    public ManagedWindowsServiceLauncher(String userName, String password) {
        this.userName = userName;
        this.password = Secret.fromString(password);
    }

    private JIDefaultAuthInfoImpl createAuth() {
        String[] tokens = userName.split("\\\\");
        if(tokens.length==2)
            return new JIDefaultAuthInfoImpl(tokens[0], tokens[1], password.toString());
        return new JIDefaultAuthInfoImpl("", userName, password.toString());
    }

    private NtlmPasswordAuthentication createSmbAuth() {
        JIDefaultAuthInfoImpl auth = createAuth();
        return new NtlmPasswordAuthentication(auth.getDomain(), auth.getUserName(), auth.getPassword());
    }

    public void launch(final SlaveComputer computer, final TaskListener listener) throws IOException, InterruptedException {
        try {
            PrintStream logger = listener.getLogger();

            logger.println(Messages.ManagedWindowsServiceLauncher_ConnectingTo(computer.getName()));
            JIDefaultAuthInfoImpl auth = createAuth();
            JISession session = JISession.createSession(auth);
            session.setGlobalSocketTimeout(60000);
            SWbemServices services = WMI.connect(session, computer.getName());

            String path = computer.getNode().getRemoteFS();
            SmbFile remoteRoot = new SmbFile("smb://" + computer.getName() + "/" + path.replace('\\', '/').replace(':', '$')+"/",createSmbAuth());

            Win32Service slaveService = services.getService("hudsonslave");
            if(slaveService==null) {
                logger.println(Messages.ManagedWindowsServiceLauncher_InstallingSlaveService());
                if(!DotNet.isInstalled(2,0,computer.getName(), auth)) {
                    // abort the launch
                    logger.println(Messages.ManagedWindowsServiceLauncher_DotNetRequired());
                    return;
                }
    
                remoteRoot.mkdirs();

                // copy exe
                logger.println(Messages.ManagedWindowsServiceLauncher_CopyingSlaveExe());
                copyAndClose(getClass().getResource("/windows-service/hudson.exe").openStream(),
                        new SmbFile(remoteRoot,"hudson-slave.exe").getOutputStream());

                copySlaveJar(logger, remoteRoot);

                // copy hudson-slave.xml
                logger.println(Messages.ManagedWindowsServiceLauncher_CopyingSlaveXml());
                String xml = WindowsSlaveInstaller.generateSlaveXml("javaw.exe","-tcp %BASE%\\port.txt");
                copyAndClose(new ByteArrayInputStream(xml.getBytes("UTF-8")),
                        new SmbFile(remoteRoot,"hudson-slave.xml").getOutputStream());

                // install it as a service
                logger.println(Messages.ManagedWindowsServiceLauncher_RegisteringService());
                Document dom = new SAXReader().read(new StringReader(xml));
                Win32Service svc = services.Get("Win32_Service").cast(Win32Service.class);
                int r = svc.Create(
                        dom.selectSingleNode("/service/id").getText(),
                        dom.selectSingleNode("/service/name").getText(),
                        path+"\\hudson-slave.exe",
                        Win32OwnProcess, 0, "Manual", true);
                if(r!=0) {
                    listener.error("Failed to create a service: "+svc.getErrorMessage(r));
                    return;
                }
                slaveService = services.getService("hudsonslave");
            } else {
                copySlaveJar(logger, remoteRoot);                
            }

            logger.println(Messages.ManagedWindowsServiceLauncher_StartingService());
            slaveService.start();

            // wait until we see the port.txt, but don't do so forever
            logger.println(Messages.ManagedWindowsServiceLauncher_WaitingForService());
            SmbFile portFile = new SmbFile(remoteRoot, "port.txt");
            for( int i=0; !portFile.exists(); i++ ) {
                if(i>=30) {
                    listener.error(Messages.ManagedWindowsServiceLauncher_ServiceDidntRespond());
                    return;
                }
                Thread.sleep(1000);
            }
            int p = readSmbFile(portFile);

            // connect
            logger.println(Messages.ManagedWindowsServiceLauncher_ConnectingToPort(p));
            final Socket s = new Socket(computer.getName(),p);

            // ready
            computer.setChannel(new BufferedInputStream(new SocketInputStream(s)),
                new BufferedOutputStream(new SocketOutputStream(s)),
                listener.getLogger(),new Listener() {
                    public void onClosed(Channel channel, IOException cause) {
                        afterDisconnect(computer,listener);
                    }
                });
        } catch (SmbException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        } catch (JIException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        } catch (DocumentException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        }
    }

    private void copySlaveJar(PrintStream logger, SmbFile remoteRoot) throws IOException {
        // copy slave.jar
        logger.println("Copying slave.jar");
        copyAndClose(Hudson.getInstance().getJnlpJars("slave.jar").getURL().openStream(),
                        new SmbFile(remoteRoot,"slave.jar").getOutputStream());
    }

    private int readSmbFile(SmbFile f) throws IOException {
        InputStream in=null;
        try {
            in = f.getInputStream();
            return Integer.parseInt(IOUtils.toString(in));
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        try {
            JIDefaultAuthInfoImpl auth = createAuth();
            JISession session = JISession.createSession(auth);
            session.setGlobalSocketTimeout(60000);
            SWbemServices services = WMI.connect(session, computer.getName());
            Win32Service slaveService = services.getService("hudsonslave");
            if(slaveService!=null) {
                listener.getLogger().println(Messages.ManagedWindowsServiceLauncher_StoppingService());
                slaveService.StopService();
            }
        } catch (UnknownHostException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        } catch (JIException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        }
    }

    private static void copyAndClose(InputStream in, OutputStream out) {
        try {
            IOUtils.copy(in,out);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        public String getDisplayName() {
            return Messages.ManagedWindowsServiceLauncher_DisplayName();
        }
    }

    static {
        Logger.getLogger("org.jinterop").setLevel(Level.WARNING);
    }
}
