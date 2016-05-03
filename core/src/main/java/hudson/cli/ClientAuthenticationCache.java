package hudson.cli;

import hudson.FilePath;
import hudson.remoting.Channel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import org.springframework.dao.DataAccessException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Properties;

/**
 * Represents the authentication credential store of the CLI client.
 *
 * <p>
 * This object encapsulates a remote manipulation of the credential store.
 * We store encrypted user names.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.351
 */
public class ClientAuthenticationCache implements Serializable {
    /**
     * Where the store should be placed.
     */
    private final FilePath store;

    /**
     * Loaded contents of the store.
     */
    private final Properties props = new Properties();

    public ClientAuthenticationCache(Channel channel) throws IOException, InterruptedException {
        store = (channel==null ? FilePath.localChannel :  channel).call(new MasterToSlaveCallable<FilePath, IOException>() {
            public FilePath call() throws IOException {
                File home = new File(System.getProperty("user.home"));
                File hudsonHome = new File(home, ".hudson");
                if (hudsonHome.exists()) {
                    return new FilePath(new File(hudsonHome, "cli-credentials"));
                }
                return new FilePath(new File(home, ".jenkins/cli-credentials"));
            }
        });
        if (store.exists()) {
            InputStream istream = store.read();
            try {
                props.load(istream);
            } finally {
                istream.close();
            }
        }
    }

    /**
     * Gets the persisted authentication for this Jenkins.
     *
     * @return {@link jenkins.model.Jenkins#ANONYMOUS} if no such credential is found, or if the stored credential is invalid.
     */
    public Authentication get() {
        Jenkins h = Jenkins.getActiveInstance();
        Secret userName = Secret.decrypt(props.getProperty(getPropertyKey()));
        if (userName==null) return Jenkins.ANONYMOUS; // failed to decrypt
        try {
            UserDetails u = h.getSecurityRealm().loadUserByUsername(userName.getPlainText());
            return new UsernamePasswordAuthenticationToken(u.getUsername(), "", u.getAuthorities());
        } catch (AuthenticationException | DataAccessException e) {
            return Jenkins.ANONYMOUS;
        }
    }

    /**
     * Computes the key that identifies this Hudson among other Hudsons that the user has a credential for.
     */
    private String getPropertyKey() {
        String url = Jenkins.getActiveInstance().getRootUrl();
        if (url!=null)  return url;
        return Secret.fromString("key").toString();
    }

    /**
     * Persists the specified authentication.
     */
    public void set(Authentication a) throws IOException, InterruptedException {
        Jenkins h = Jenkins.getActiveInstance();

        // make sure that this security realm is capable of retrieving the authentication by name,
        // as it's not required.
        UserDetails u = h.getSecurityRealm().loadUserByUsername(a.getName());
        props.setProperty(getPropertyKey(), Secret.fromString(u.getUsername()).getEncryptedValue());

        save();
    }

    /**
     * Removes the persisted credential, if there's one.
     */
    public void remove() throws IOException, InterruptedException {
        if (props.remove(getPropertyKey())!=null)
            save();
    }

    private void save() throws IOException, InterruptedException {
        OutputStream os = store.write();
        try {
            props.store(os,"Credential store");
        } finally {
            os.close();
        }
        // try to protect this file from other users, if we can.
        store.chmod(0600);
    }
}
