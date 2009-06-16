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
package hudson.lifecycle;

import hudson.FilePath;
import hudson.Launcher.LocalLauncher;
import hudson.remoting.Callable;
import hudson.remoting.Engine;
import hudson.remoting.jnlp.MainDialog;
import hudson.remoting.jnlp.MainMenu;
import hudson.util.StreamTaskListener;
import hudson.util.jna.DotNet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;

/**
 * @author Kohsuke Kawaguchi
 */
public class WindowsSlaveInstaller implements Callable<Void,RuntimeException>, ActionListener {
    /**
     * Root directory of this slave.
     * String, not File because the platform can be different.
     */
    private final String rootDir;

    private transient Engine engine;
    private transient MainDialog dialog;

    public WindowsSlaveInstaller(String rootDir) {
        this.rootDir = rootDir;
    }

    public Void call() {
        if(File.separatorChar=='/') return null;    // not Windows
        if(System.getProperty("hudson.showWindowsServiceInstallLink")==null)
            return null;    // only show this when it makes sense, which is when we run from JNLP

        dialog = MainDialog.get();
        if(dialog==null)     return null;    // can't find the main window. Maybe not running with GUI

        // capture the engine
        engine = Engine.current();

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                MainMenu mainMenu = dialog.getMainMenu();
                JMenu m = mainMenu.getFileMenu();
                JMenuItem menu = new JMenuItem(Messages.WindowsInstallerLink_DisplayName(), KeyEvent.VK_W);
                menu.addActionListener(WindowsSlaveInstaller.this);
                m.add(menu);
                mainMenu.commit();
            }
        });

        return null;
    }

    /**
     * Called when the install menu is selected
     */
    public void actionPerformed(ActionEvent e) {
        int r = JOptionPane.showConfirmDialog(dialog,
                "This will install a slave agent as a Windows service,\n" +
                "so that this slave will connect to Hudson as soon as the machine boots.\n" +
                "Do you want to proceed with installation?",
                Messages.WindowsInstallerLink_DisplayName(),
                JOptionPane.OK_CANCEL_OPTION);
        if(r!=JOptionPane.OK_OPTION)    return;

        if(!DotNet.isInstalled(2,0)) {
            JOptionPane.showMessageDialog(dialog,".NET Framework 2.0 or later is required for this feature",
                    Messages.WindowsInstallerLink_DisplayName(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        final File dir = new File(rootDir);


        try {
            final File slaveExe = new File(dir, "hudson-slave.exe");
            FileUtils.copyURLToFile(getClass().getResource("/windows-service/hudson.exe"), slaveExe);

            // write out the descriptor
            URL jnlp = new URL(engine.getHudsonUrl(),"computer/"+engine.slaveName+"/slave-agent.jnlp");
            String xml = generateSlaveXml(System.getProperty("java.home")+"\\bin\\java.exe", "-jnlpUrl "+jnlp.toExternalForm());
            FileUtils.writeStringToFile(new File(dir, "hudson-slave.xml"),xml,"UTF-8");

            // copy slave.jar
            URL slaveJar = new URL(engine.getHudsonUrl(),"jnlpJars/remoting.jar");
            File dstSlaveJar = new File(dir,"slave.jar").getCanonicalFile();
            if(!dstSlaveJar.exists()) // perhaps slave.jar is already there?
                FileUtils.copyURLToFile(slaveJar,dstSlaveJar);

            // install as a service
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StreamTaskListener task = new StreamTaskListener(baos);
            r = new LocalLauncher(task).launch().cmds(slaveExe, "install").stdout(task).pwd(dir).join();
            if(r!=0) {
                JOptionPane.showMessageDialog(
                    dialog,baos.toString(),"Error",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            r = JOptionPane.showConfirmDialog(dialog,
                    "Installation was successful. Would you like to\n" +
                    "Stop this slave agent and start the newly installed service?",
                    Messages.WindowsInstallerLink_DisplayName(),
                    JOptionPane.OK_CANCEL_OPTION);
            if(r!=JOptionPane.OK_OPTION)    return;

            // let the service start after we close our connection, to avoid conflicts
            Runtime.getRuntime().addShutdownHook(new Thread("service starter") {
                public void run() {
                    try {
                        StreamTaskListener task = new StreamTaskListener(System.out);
                        int r = new LocalLauncher(task).launch().cmds(slaveExe, "start").stdout(task).pwd(dir).join();
                        task.getLogger().println(r==0?"Successfully started":"start service failed. Exit code="+r);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            System.exit(0);
        } catch (Exception t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            JOptionPane.showMessageDialog(
                dialog,sw.toString(),"Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    public static String generateSlaveXml(String java, String args) throws IOException {
        String xml = IOUtils.toString(WindowsSlaveInstaller.class.getResourceAsStream("/windows-service/hudson-slave.xml"), "UTF-8");
        xml = xml.replace("@JAVA@", java);
        xml = xml.replace("@ARGS@", args);
        return xml;
    }

    private static final long serialVersionUID = 1L;
}
