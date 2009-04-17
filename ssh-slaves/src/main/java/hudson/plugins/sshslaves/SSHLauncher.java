package hudson.plugins.sshslaves;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SFTPException;
import com.trilead.ssh2.SFTPv3Client;
import com.trilead.ssh2.SFTPv3FileAttributes;
import com.trilead.ssh2.SFTPv3FileHandle;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.IOException2;
import hudson.util.StreamCopyThread;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A computer launcher that tries to start a linux slave by opening an SSH connection and trying to find java.
 */
public class SSHLauncher extends ComputerLauncher {

    /**
     * Field host
     */
    private final String host;

    /**
     * Field port
     */
    private final int port;

    /**
     * Field username
     */
    private final String username;

    /**
     * Field password
     *
     * @todo remove password once authentication is stored in the descriptor.
     */
    private final String password;

    /**
     * Field privatekey
     */
    private final String privatekey;

    /**
     * Field connection
     */
    private transient Connection connection;

    /**
     * Size of the buffer used to copy the slave jar file to the slave.
     */
    private static final int BUFFER_SIZE = 2048;

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host       The host to connect to.
     * @param port       The port to connect on.
     * @param username   The username to connect as.
     * @param password   The password to connect with.
     * @param privatekey The ssh privatekey to connect with.
     */
    @DataBoundConstructor
    public SSHLauncher(String host, int port, String username, String password, String privatekey) {
        this.host = host;
        this.port = port == 0 ? 22 : port;
        this.username = username;
        this.password = password;
        this.privatekey = privatekey;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * Gets the formatted current time stamp.
     *
     * @return the formatted current time stamp.
     */
    private static String getTimestamp() {
        return String.format("[%1$tD %1$tT]", new Date());
    }

    /**
     * Returns the remote root workspace (without trailing slash).
     *
     * @param computer The slave computer to get the root workspace of.
     *
     * @return the remote root workspace (without trailing slash).
     */
    private static String getWorkingDirectory(SlaveComputer computer) {
        String workingDirectory = computer.getNode().getRemoteFS();
        while (workingDirectory.endsWith("/")) {
            workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
        }
        return workingDirectory;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void launch(final SlaveComputer computer, final StreamTaskListener listener) {
        connection = new Connection(host, port);
        try {
            openConnection(listener);

            reportEnvironment(listener);

            String java = null;
            outer:
            for (JavaProvider provider : javaProviders) {
                for (String javaCommand : provider.getJavas(listener, connection)) {
                    try {
                        java = checkJavaVersion(listener, javaCommand);
                        if (java != null) {
                            break outer;
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
            if (java == null) {
                throw new IOException("Could not find any known supported java version");
            }

            String workingDirectory = getWorkingDirectory(computer);

            copySlaveJar(listener, workingDirectory);

            startSlave(computer, listener, java, workingDirectory);

            PluginImpl.register(connection);
        } catch (RuntimeException e) {
            e.printStackTrace(listener.error(Messages.SSHLauncher_UnexpectedError()));
        } catch (Error e) {
            e.printStackTrace(listener.error(Messages.SSHLauncher_UnexpectedError()));
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
            connection.close();
            connection = null;
            listener.getLogger().println(Messages.SSHLauncher_ConnectionClosed(getTimestamp()));
        }
    }

    /**
     * Starts the slave process.
     *
     * @param computer         The computer.
     * @param listener         The listener.
     * @param java             The full path name of the java executable to use.
     * @param workingDirectory The working directory from which to start the java process.
     *
     * @throws IOException If something goes wrong.
     */
    private void startSlave(SlaveComputer computer, final StreamTaskListener listener, String java,
                            String workingDirectory) throws IOException {
        final Session session = connection.openSession();
        // TODO handle escaping fancy characters in paths
        session.execCommand("cd " + workingDirectory + " && " + java + " -jar slave.jar");
        final StreamGobbler out = new StreamGobbler(session.getStdout());
        final StreamGobbler err = new StreamGobbler(session.getStderr());

        // capture error information from stderr. this will terminate itself
        // when the process is killed.
        new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(),
                err, listener.getLogger()).start();

        try {
            computer.setChannel(out, session.getStdin(), listener.getLogger(), new Channel.Listener() {
                public void onClosed(Channel channel, IOException cause) {
                    if (cause != null) {
                        cause.printStackTrace(listener.error(hudson.model.Messages.Slave_Terminated(getTimestamp())));
                    }
                    try {
                        session.close();
                    } catch (Throwable t) {
                        t.printStackTrace(listener.error(Messages.SSHLauncher_ErrorWhileClosingConnection()));
                    }
                    try {
                        out.close();
                    } catch (Throwable t) {
                        t.printStackTrace(listener.error(Messages.SSHLauncher_ErrorWhileClosingConnection()));
                    }
                    try {
                        err.close();
                    } catch (Throwable t) {
                        t.printStackTrace(listener.error(Messages.SSHLauncher_ErrorWhileClosingConnection()));
                    }
                }
            });

        } catch (InterruptedException e) {
            session.close();
            throw new IOException2(Messages.SSHLauncher_AbortedDuringConnectionOpen(), e);
        }
    }

    /**
     * Method copies the slave jar to the remote system.
     *
     * @param listener         The listener.
     * @param workingDirectory The directory into whihc the slave jar will be copied.
     *
     * @throws IOException If something goes wrong.
     */
    private void copySlaveJar(StreamTaskListener listener, String workingDirectory) throws IOException {
        String fileName = workingDirectory + "/slave.jar";

        listener.getLogger().println(Messages.SSHLauncher_StartingSFTPClient(getTimestamp()));
        SFTPv3Client sftpClient = null;
        try {
            sftpClient = new SFTPv3Client(connection);

            try {
                // TODO decide best permissions and handle errors if exists already
                SFTPv3FileAttributes fileAttributes;
                try {
                    fileAttributes = sftpClient.stat(workingDirectory);
                } catch (SFTPException e) {
                    fileAttributes = null;
                }
                if (fileAttributes == null) {
                    listener.getLogger().println(Messages.SSHLauncher_RemoteFSDoesNotExist(getTimestamp(),
                            workingDirectory));
                    // TODO mkdir -p mode
                    sftpClient.mkdir(workingDirectory, 0700);
                } else if (fileAttributes.isRegularFile()) {
                    throw new IOException(Messages.SSHLauncher_RemoteFSIsAFile(workingDirectory));
                }

                try {
                    // try to delete the file in case the slave we are copying is shorter than the slave
                    // that is already there
                    sftpClient.rm(fileName);
                } catch (IOException e) {
                    // the file did not exist... so no need to delete it!
                }

                listener.getLogger().println(Messages.SSHLauncher_CopyingSlaveJar(getTimestamp()));
                SFTPv3FileHandle fileHandle = sftpClient.createFile(fileName);

                InputStream is = null;
                try {
                    is = Hudson.getInstance().servletContext.getResourceAsStream("/WEB-INF/slave.jar");
                    byte[] buf = new byte[BUFFER_SIZE];

                    int count = 0;
                    int len;
                    try {
                        while ((len = is.read(buf)) != -1) {
                            sftpClient.write(fileHandle, (long) count, buf, 0, len);
                            count += len;
                        }
                        listener.getLogger().println(Messages.SSHLauncher_CopiedXXXBytes(getTimestamp(), count));
                    } catch (Exception e) {
                        throw new IOException2(Messages.SSHLauncher_ErrorCopyingSlaveJar(), e);
                    }
                } finally {
                    if (is != null) {
                        is.close();
                    }
                }
            } catch (Exception e) {
                throw new IOException2(Messages.SSHLauncher_ErrorCopyingSlaveJar(), e);
            }
        } finally {
            if (sftpClient != null) {
                sftpClient.close();
            }
        }
    }

    private void reportEnvironment(StreamTaskListener listener) throws IOException {
        Session session = connection.openSession();
        try {
            session.execCommand("set");
            StreamGobbler out = new StreamGobbler(session.getStdout());
            StreamGobbler err = new StreamGobbler(session.getStderr());
            try {
                BufferedReader r1 = new BufferedReader(new InputStreamReader(out));
                BufferedReader r2 = new BufferedReader(new InputStreamReader(err));

                // TODO make sure this works with IBM JVM & JRocket

                String line;
                outer:
                for (BufferedReader r : new BufferedReader[]{r1, r2}) {
                    while (null != (line = r.readLine())) {
                        listener.getLogger().println(line);
                    }
                }
            } finally {
                out.close();
                err.close();
            }
        } finally {
            session.close();
        }
    }

    private String checkJavaVersion(StreamTaskListener listener, String javaCommand) throws IOException {
        listener.getLogger().println(Messages.SSHLauncher_CheckingDefaultJava(getTimestamp()));
        String line = null;
        Session session = connection.openSession();
        try {
            session.execCommand(javaCommand + " -version");
            StreamGobbler out = new StreamGobbler(session.getStdout());
            StreamGobbler err = new StreamGobbler(session.getStderr());
            try {
                BufferedReader r1 = new BufferedReader(new InputStreamReader(out));
                BufferedReader r2 = new BufferedReader(new InputStreamReader(err));

                // TODO make sure this works with IBM JVM & JRocket

                outer:
                for (BufferedReader r : new BufferedReader[]{r1, r2}) {
                    while (null != (line = r.readLine())) {
                        if (line.startsWith("java version \"")) {
                            break outer;
                        }
                    }
                }
            } finally {
                out.close();
                err.close();
            }
        } finally {
            session.close();
        }

        if (line == null || !line.startsWith("java version \"")) {
            throw new IOException("The default version of java is either unsupported version or unknown");
        }

        line = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
        listener.getLogger().println(Messages.SSHLauncher_JavaVersionResult(getTimestamp(), javaCommand, line));

        // TODO make this version check a bit less hacky
        if (line.compareTo("1.5") < 0) {
            // TODO find a java that is at least 1.5
            throw new IOException(Messages.SSHLauncher_NoJavaFound());
        }
        return javaCommand;
    }

    private void openConnection(StreamTaskListener listener) throws IOException {
        listener.getLogger().println(Messages.SSHLauncher_OpeningSSHConnection(getTimestamp(), host + ":" + port));
        connection.connect();

        // TODO if using a key file, use the key file instead of password
        boolean isAuthenticated = false;
        if (privatekey != null && privatekey.length() > 0) {
            File key = new File(privatekey);
            if (key.exists()) {
                listener.getLogger()
                        .println(Messages.SSHLauncher_AuthenticatingPublicKey(getTimestamp(), username, privatekey));
                isAuthenticated = connection.authenticateWithPublicKey(username, key, password);
            }
        }
        if (!isAuthenticated) {
            listener.getLogger()
                    .println(Messages.SSHLauncher_AuthenticatingUserPass(getTimestamp(), username, "******"));
            isAuthenticated = connection.authenticateWithPassword(username, password);
        }
        if (isAuthenticated && connection.isAuthenticationComplete()) {
            listener.getLogger().println(Messages.SSHLauncher_AuthenticationSuccessful(getTimestamp()));
        } else {
            listener.getLogger().println(Messages.SSHLauncher_AuthenticationFailed(getTimestamp()));
            connection.close();
            connection = null;
            listener.getLogger().println(Messages.SSHLauncher_ConnectionClosed(getTimestamp()));
            throw new IOException(Messages.SSHLauncher_AuthenticationFailedException());
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void afterDisconnect(SlaveComputer slaveComputer, StreamTaskListener listener) {
        String workingDirectory = getWorkingDirectory(slaveComputer);
        String fileName = workingDirectory + "/slave.jar";

        if (connection != null) {

            SFTPv3Client sftpClient = null;
            try {
                sftpClient = new SFTPv3Client(connection);
                sftpClient.rm(fileName);
            } catch (Exception e) {
                e.printStackTrace(listener.error(Messages.SSHLauncher_ErrorDeletingFile(getTimestamp())));
            } finally {
                if (sftpClient != null) {
                    sftpClient.close();
                }
            }

            connection.close();
            PluginImpl.unregister(connection);
            connection = null;
            listener.getLogger().println(Messages.SSHLauncher_ConnectionClosed(getTimestamp()));
        }
        super.afterDisconnect(slaveComputer, listener);
    }

    /**
     * Getter for property 'host'.
     *
     * @return Value for property 'host'.
     */
    public String getHost() {
        return host;
    }

    /**
     * Getter for property 'port'.
     *
     * @return Value for property 'port'.
     */
    public int getPort() {
        return port;
    }

    /**
     * Getter for property 'username'.
     *
     * @return Value for property 'username'.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Getter for property 'password'.
     *
     * @return Value for property 'password'.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Getter for property 'privatekey'.
     *
     * @return Value for property 'privatekey'.
     */
    public String getPrivatekey() {
        return privatekey;
    }

    /**
     * {@inheritDoc}
     */
    public Descriptor<ComputerLauncher> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Field DESCRIPTOR
     */
    public static final Descriptor<ComputerLauncher> DESCRIPTOR = new DescriptorImpl();

    private static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        // TODO move the authentication storage to descriptor... see SubversionSCM.java

        // TODO add support for key files

        /**
         * Constructs a new DescriptorImpl.
         */
        protected DescriptorImpl() {
            super(SSHLauncher.class);
        }

        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
        }

    }

    private static final List<JavaProvider> javaProviders = Arrays.<JavaProvider>asList(
            new DefaultJavaProvider()
    );

    private static interface JavaProvider {

        List<String> getJavas(StreamTaskListener listener, Connection connection);
    }

    private static class DefaultJavaProvider implements JavaProvider {

        public List<String> getJavas(StreamTaskListener listener, Connection connection) {
            return Arrays.asList("java",
                    "/usr/bin/java",
                    "/usr/java/default/bin/java",
                    "/usr/java/latest/bin/java");
        }
    }
}
