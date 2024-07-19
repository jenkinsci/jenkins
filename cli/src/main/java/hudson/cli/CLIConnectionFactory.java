package hudson.cli;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Fluent-API to instantiate {@link CLI}.
 *
 * @author Kohsuke Kawaguchi
 */
public class CLIConnectionFactory {
    String authorization;
    boolean noCertificateCheck;

    /**
     * For CLI connection that goes through HTTP, sometimes you need
     * to pass in the custom authentication header (before Jenkins even get to authenticate
     * the CLI channel.) This method lets you specify the value of this header.
     */
    public CLIConnectionFactory authorization(String value) {
        this.authorization = value;
        return this;
    }

    /**
     * Skip TLS certificate and hostname verification checks.
     *
     * @since 2.444
     */
    public CLIConnectionFactory noCertificateCheck(boolean value) {
        this.noCertificateCheck = value;
        return this;
    }

    /**
     * Convenience method to call {@link #authorization} with the HTTP basic authentication.
     * Currently unused.
     */
    public CLIConnectionFactory basicAuth(String username, String password) {
        return basicAuth(username + ':' + password);
    }

    /**
     * Convenience method to call {@link #authorization} with the HTTP basic authentication.
     * Cf. {@code BasicHeaderApiTokenAuthenticator}.
     */
    public CLIConnectionFactory basicAuth(String userInfo) {
        return authorization("Basic " + Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Convenience method to call {@link #authorization} with the HTTP bearer authentication.
     * Cf. {@code BasicHeaderApiTokenAuthenticator}.
     */
    public CLIConnectionFactory bearerAuth(String bearerToken) {
        return authorization("Bearer " + bearerToken);
    }
}
