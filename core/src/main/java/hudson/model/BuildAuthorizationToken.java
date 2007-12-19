package hudson.model;

import com.thoughtworks.xstream.converters.basic.AbstractBasicConverter;
import hudson.Util;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;

/**
 * Authorization token to allow projects to trigger themselves under the secured environment.
 *
 * @author Kohsuke Kawaguchi
 * @see BuildableItem
 */
public final class BuildAuthorizationToken {
    private final String token;

    public BuildAuthorizationToken(String token) {
        this.token = token;
    }

    public static BuildAuthorizationToken create(StaplerRequest req) {
        if (req.getParameter("pseudoRemoteTrigger") != null)
            return new BuildAuthorizationToken(Util.fixEmpty(req.getParameter("authToken")));
        else
            return null;
    }

    public static void checkPermission(AbstractProject project, BuildAuthorizationToken token, StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (!Hudson.getInstance().isUseSecurity())
            return;    // everyone is authorized

        if(token!=null) {
            if(token.token != null) {
                //check the provided token
                String providedToken = req.getParameter("token");
                if (providedToken != null && providedToken.equals(token.token))
                    return;
            }
        }

        project.checkPermission(AbstractProject.BUILD);
    }

    public String getToken() {
        return token;
    }

    public static final class ConverterImpl extends AbstractBasicConverter {
        public boolean canConvert(Class type) {
            return type== BuildAuthorizationToken.class;
        }

        protected Object fromString(String str) {
            return new BuildAuthorizationToken(str);
        }

        protected String toString(Object obj) {
            return ((BuildAuthorizationToken)obj).token;
        }
    }
}
