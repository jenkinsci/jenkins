package jenkins.security;

import jakarta.servlet.Filter;
import java.util.List;

/**
 * {@link Filter} that performs HTTP basic authentication based on API token.
 *
 * <p>
 * Normally the filter chain would also contain another filter that handles BASIC
 * auth with the real password. Care must be taken to ensure that this doesn't
 * interfere with the other.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated as of 1.576
 *      Use {@link BasicHeaderProcessor}
 */
@Deprecated
public class ApiTokenFilter extends BasicHeaderProcessor {
    @Override
    protected List<? extends BasicHeaderAuthenticator> all() {
        return List.of(new BasicHeaderApiTokenAuthenticator());
    }
}
