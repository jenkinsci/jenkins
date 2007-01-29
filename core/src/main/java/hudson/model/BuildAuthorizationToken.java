package hudson.model;

import com.thoughtworks.xstream.converters.basic.AbstractBasicConverter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
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
            return new BuildAuthorizationToken(req.getParameter("authToken"));
        else
            return null;
    }

    public static void startBuildIfAuthorized(BuildAuthorizationToken token, BuildableItem job, StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(token==null || token.authorizedToStartBuild(req,rsp)) {
            job.scheduleBuild();
            rsp.forwardToPreviousPage(req);
        }
    }

    public boolean authorizedToStartBuild(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (!Hudson.getInstance().isUseSecurity())
            // everyone is authorized
            return true;

        if(token != null) {
            //check the provided token
            String providedToken = req.getParameter("token");
            if (providedToken != null && providedToken.equals(token))
                return true;
        }

        // otherwise it must be an admin
        return Hudson.adminCheck(req, rsp);
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
