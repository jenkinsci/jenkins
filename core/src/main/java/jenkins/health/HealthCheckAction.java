package jenkins.health;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import java.util.HashMap;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.json.JsonHttpResponse;

/**
 * Provides a health check action for Jenkins.
 */
@Extension
@Restricted(DoNotUse.class)
public final class HealthCheckAction extends InvisibleAction implements UnprotectedRootAction {
    private static final Logger LOGGER = Logger.getLogger(HealthCheckAction.class.getName());

    @Override
    public String getUrlName() {
        return "healthCheck";
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void init() {
        var names = new HashMap<String, String>();
        for (var healthCheck : ExtensionList.lookup(HealthCheck.class)) {
            var name = healthCheck.getName();
            var previousValue = names.put(name, healthCheck.getClass().getName());
            if (previousValue != null) {
                LOGGER.warning(() -> "Ignoring duplicate health check with name " + name + " from " + healthCheck.getClass().getName() + " as it is already defined by " + previousValue);
            }
        }
    }

    public HttpResponse doIndex() {
        boolean success = true;
        var checks = new JSONArray();
        var names = new HashMap<String, String>();
        for (var healthCheck : ExtensionList.lookup(HealthCheck.class)) {
            var check = healthCheck.check();
            var name = healthCheck.getName();
            var previousValue = names.put(name, healthCheck.getClass().getName());
            if (previousValue == null) {
                success &= check;
                checks.add(new JSONObject().element("name", name).element("result", check));
            }
        }
        return new JsonHttpResponse(new JSONObject().element("status", success).element("data", checks), success ? 200 : 503);
    }
}
