package hudson.cli;

import hudson.Extension;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.nio.NioChannelHub;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;

/**
 * {@link CliProtocol} Version 2, which adds transport encryption.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.467
 */
@Extension @Symbol("cli2")
public class CliProtocol2 extends CliProtocol {
    @Override
    public String getName() {
        return "CLI2-connect";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOptIn() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return "Jenkins CLI Protocol/2";
    }

    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {
        new Handler2(nio.getHub(), socket).run();
    }

    protected static class Handler2 extends Handler {
        /**
         * @deprecated as of 1.559
         *      Use {@link #Handler2(NioChannelHub, Socket)}
         */
        @Deprecated
        public Handler2(Socket socket) {
            super(socket);
        }

        public Handler2(NioChannelHub hub, Socket socket) {
            super(hub, socket);
        }

        @Override
        public void run() throws IOException, InterruptedException {
            try {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF("Welcome");

                // perform coin-toss and come up with a session key to encrypt data
                Connection c = new Connection(socket);
                byte[] secret = c.diffieHellman(true).generateSecret();
                SecretKey sessionKey = new SecretKeySpec(Connection.fold(secret,128/8),"AES");
                c = c.encryptConnection(sessionKey,"AES/CFB8/NoPadding");

                try {
                    // HACK: TODO: move the transport support into modules
                    Class<?> cls = Jenkins.getActiveInstance().pluginManager.uberClassLoader.loadClass("org.jenkinsci.main.modules.instance_identity.InstanceIdentity");
                    Object iid = cls.getDeclaredMethod("get").invoke(null);
                    PrivateKey instanceId = (PrivateKey)cls.getDeclaredMethod("getPrivate").invoke(iid);

                    // send a signature to prove our identity
                    Signature signer = Signature.getInstance("SHA1withRSA");
                    signer.initSign(instanceId);
                    signer.update(secret);
                    c.writeByteArray(signer.sign());
                } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new Error(e);
                }

                runCli(c);
            } catch (GeneralSecurityException e) {
                throw new IOException("Failed to encrypt the CLI channel",e);
            }
        }
    }
}
