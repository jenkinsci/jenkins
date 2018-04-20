package hudson.cli;

import com.google.common.annotations.VisibleForTesting;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.HMACConfidentialKey;

import javax.annotation.Nonnull;

/**
 * Represents the authentication credential store of the CLI client.
 *
 * <p>
 * This object encapsulates a remote manipulation of the credential store.
 * We store encrypted user names.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.351
 * @deprecated Assumes Remoting, and vulnerable to JENKINS-12543.
 */
@Deprecated
public class ClientAuthenticationCache implements Serializable {

    private static final HMACConfidentialKey MAC = new HMACConfidentialKey(ClientAuthenticationCache.class, "MAC");
    private static final Logger LOGGER = Logger.getLogger(ClientAuthenticationCache.class.getName());
    
    /**
     * Where the store should be placed.
     */
    private final FilePath store;

    /**
     * Loaded contents of the store.
     */
    @VisibleForTesting
    final Properties props = new Properties();

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
            try (InputStream istream = store.read()) {
                props.load(istream);
            }
        }
    }

    /**
     * Gets the persisted authentication for this Jenkins.
     *
     * @return {@link jenkins.model.Jenkins#ANONYMOUS} if no such credential is found, or if the stored credential is invalid.
     */
    public @Nonnull Authentication get() {
        Jenkins h = Jenkins.getActiveInstance();
        String val = props.getProperty(getPropertyKey());
        if (val == null) {
            LOGGER.finer("No stored CLI authentication");
            return Jenkins.ANONYMOUS;
        }
        Secret oldSecret = Secret.decrypt(val);
        if (oldSecret != null) {
            LOGGER.log(Level.FINE, "Ignoring insecure stored CLI authentication for {0}", oldSecret.getPlainText());
            return Jenkins.ANONYMOUS;
        }
        int idx = val.lastIndexOf(':');
        if (idx == -1) {
            LOGGER.log(Level.FINE, "Ignoring malformed stored CLI authentication: {0}", val);
            return Jenkins.ANONYMOUS;
        }
        String username = val.substring(0, idx);
        if (!MAC.checkMac(username, val.substring(idx + 1))) {
            LOGGER.log(Level.FINE, "Ignoring stored CLI authentication due to MAC mismatch: {0}", val);
            return Jenkins.ANONYMOUS;
        }
        try {
            UserDetails u = h.getSecurityRealm().loadUserByUsername(username);
            LOGGER.log(Level.FINER, "Loaded stored CLI authentication for {0}", username);
            return new UsernamePasswordAuthenticationToken(u.getUsername(), "", u.getAuthorities());
        } catch (AuthenticationException | DataAccessException e) {
            //TODO there is no check to be consistent with User.ALLOW_NON_EXISTENT_USER_TO_LOGIN
            LOGGER.log(Level.FINE, "Stored CLI authentication did not correspond to a valid user: " + username, e);
            return Jenkins.ANONYMOUS;
        }
    }

    /**
     * Computes the key that identifies this Hudson among other Hudsons that the user has a credential for.
     */
    @VisibleForTesting
    String getPropertyKey() {
        Jenkins j = Jenkins.getActiveInstance();
        String url = j.getRootUrl();
        if (url!=null)  return url;
        
        return j.getLegacyInstanceId();
    }

    /**
     * Persists the specified authentication.
     */
    public void set(Authentication a) throws IOException, InterruptedException {
        Jenkins h = Jenkins.getActiveInstance();

        // make sure that this security realm is capable of retrieving the authentication by name,
        // as it's not required.
        UserDetails u = h.getSecurityRealm().loadUserByUsername(a.getName());
        String username = u.getUsername();
        props.setProperty(getPropertyKey(), username + ":" + MAC.mac(username));

        save();
    }

    /**
     * Removes the persisted credential, if there's one.
     */
    public void remove() throws IOException, InterruptedException {
        if (props.remove(getPropertyKey())!=null)
            save();
    }

    @VisibleForTesting
    void save() throws IOException, InterruptedException {
        try (OutputStream os = store.write()) {
            props.store(os, "Credential store");
        }
        // try to protect this file from other users, if we can.
        store.chmod(0600);
    }
}
