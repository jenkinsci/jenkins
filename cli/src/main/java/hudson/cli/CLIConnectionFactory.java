package hudson.cli;

import org.apache.commons.codec.binary.Base64;


/**
 * Fluent-API to instantiate {@link CLI}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class CLIConnectionFactory {
    String authorization;

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
     * Convenience method to call {@link #authorization} with the HTTP basic authentication.
     * Currently unused.
     */
    public CLIConnectionFactory basicAuth(String username, String password) {
        return basicAuth(username+':'+password);
    }

    /**
     * Convenience method to call {@link #authorization} with the HTTP basic authentication.
     * Cf. {@code BasicHeaderApiTokenAuthenticator}.
     */
    public CLIConnectionFactory basicAuth(String userInfo) {
        return authorization("Basic " + new String(Base64.encodeBase64((userInfo).getBytes())));
    }

}
