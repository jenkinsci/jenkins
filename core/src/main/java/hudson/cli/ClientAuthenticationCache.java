package hudson.cli;

import com.google.common.annotations.VisibleForTesting;
import hudson.FilePath;
import hudson.model.User;
import hudson.remoting.Channel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.security.seed.UserSeedProperty;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import org.springframework.dao.DataAccessException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.HMACConfidentialKey;

import javax.annotation.Nonnull;

import javax.annotation.CheckForNull;

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
    private static final String VERIFICATION_FRAGMENT_SEPARATOR = "_";
    private static final String USERNAME_VERIFICATION_SEPARATOR = ":";
    private static final String VERSION_2 = "v2";

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
        int idx = val.lastIndexOf(USERNAME_VERIFICATION_SEPARATOR);
        if (idx == -1) {
            LOGGER.log(Level.FINE, "Ignoring malformed stored CLI authentication: {0}", val);
            return Jenkins.ANONYMOUS;
        }
        String username = val.substring(0, idx);
        String verificationPart = val.substring(idx + 1);
        int indexOfSeparator = verificationPart.indexOf(VERIFICATION_FRAGMENT_SEPARATOR);
        if (indexOfSeparator == -1) {
            return legacy(username, verificationPart, val);
        }
        
        /*
         * Format of the cache data: [username]:[verificationToken]
         * Where the verificationToken is: [mac]_[version]_[restOfFragments]
         */

        String[] verificationFragments = verificationPart.split(VERIFICATION_FRAGMENT_SEPARATOR);
        if (verificationFragments.length < 2) {
            LOGGER.log(Level.FINE, "Ignoring malformed stored CLI authentication verification: {0}", val);
            return Jenkins.ANONYMOUS;
        }

        // the mac is only verifying the username
        String macFragment = verificationFragments[0];
        String version = verificationFragments[1];
        String[] restOfFragments = Arrays.copyOfRange(verificationFragments, 2, verificationFragments.length);

        Authentication authFromVersion;
        if (VERSION_2.equals(version)) {
            authFromVersion = version2(username, restOfFragments, val);
        } else {
            LOGGER.log(Level.FINE, "Unrecognized version for stored CLI authentication verification: {0}", val);
            return Jenkins.ANONYMOUS;
        }

        if (authFromVersion != null) {
            return authFromVersion;
        }

        return getUserAuthIfValidMac(username, macFragment, val);
    }
    
    private Authentication legacy(String username, String mac, String fullValueStored){
        return getUserAuthIfValidMac(username, mac, fullValueStored);
    }

    /**
     * restOfFragments format: [userSeed]
     * 
     * @return {@code null} when the method wants to let the default behavior to proceed
     */
    private @CheckForNull Authentication version2(String username, String[] restOfFragments, String fullValueStored){
        if (restOfFragments.length != 1) {
            LOGGER.log(Level.FINE, "Number of fragments invalid for stored CLI authentication verification: {0}", fullValueStored);
            return Jenkins.ANONYMOUS;
        }

        if (UserSeedProperty.DISABLE_USER_SEED) {
            return null;
        }

        User user = User.getById(username, false);
        if (user == null) {
            LOGGER.log(Level.FINE, "User not found for stored CLI authentication verification: {0}", fullValueStored);
            return Jenkins.ANONYMOUS;
        }

        UserSeedProperty property = user.getProperty(UserSeedProperty.class);
        if (property == null) {
            LOGGER.log(Level.INFO, "User does not have a user seed but one is contained in CLI authentication: {0}", fullValueStored);
            return Jenkins.ANONYMOUS;
        }

        String receivedUserSeed = restOfFragments[0];
        String actualUserSeed = property.getSeed();
        if (!receivedUserSeed.equals(actualUserSeed)) {
            LOGGER.log(Level.FINE, "Actual user seed does not correspond to the one in stored CLI authentication: {0}", fullValueStored);
            return Jenkins.ANONYMOUS;
        }

        return null;
    }
    
    private Authentication getUserAuthIfValidMac(String username, String mac, String fullValueStored) {
        if (!MAC.checkMac(username, mac)) {
            LOGGER.log(Level.FINE, "Ignoring stored CLI authentication due to MAC mismatch: {0}", fullValueStored);
            return Jenkins.ANONYMOUS;
        }
        try {
            UserDetails u = Jenkins.get().getSecurityRealm().loadUserByUsername(username);
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

        User user;
        if (a instanceof AnonymousAuthenticationToken) {
            user = null;
        } else {
            user = User.getById(a.getName(), false);
        }

        if (user == null) {
            // anonymous case or user not existing case, but normally should not occur
            // since the only call to it is by LoginCommand after a non-anonymous login.
            setUsingLegacyMethod(username);
            return;
        }

        String userSeed;
        UserSeedProperty userSeedProperty = user.getProperty(UserSeedProperty.class);
        if (userSeedProperty == null) {
            userSeed = "no-user-seed";
        } else {
            userSeed = userSeedProperty.getSeed();
        }
        String mac = getMacOf(username);
        String validationFragment = String.join(VERIFICATION_FRAGMENT_SEPARATOR, mac, VERSION_2, userSeed);

        String propertyValue = username + USERNAME_VERIFICATION_SEPARATOR + validationFragment;
        props.setProperty(getPropertyKey(), propertyValue);

        save();
    }

    @VisibleForTesting
    void setUsingLegacyMethod(String username) throws IOException, InterruptedException {
        props.setProperty(getPropertyKey(), username + USERNAME_VERIFICATION_SEPARATOR + getMacOf(username));
        save();
    }
    
    @VisibleForTesting
    @Nonnull String getMacOf(@Nonnull String value){
        return MAC.mac(value);
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
