package jenkins;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.util.HttpResponses;

import java.util.Objects;
import net.sf.json.JSONObject;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

import static hudson.Functions.extractPluginNameFromIconSrc;

@Extension
@Restricted(NoExternalUse.class)
public class Symbols implements RootAction {

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "symbols";
    }

    /**
     * Returns a Jenkins Symbol
     *
     * @param request The request.
     * @return The JSON response.
     */
    public HttpResponse doIndex(StaplerRequest request) {
        String symbol = request.getParameter("symbol");

        if (symbol == null) {
            return HttpResponses.errorJSON("Mandatory parameter 'symbol' not specified.");
        }

        symbol = symbol.replace("symbol-", "");

        String title = Objects.toString(request.getParameter("title"), "");
        String tooltip = Objects.toString(request.getParameter("tooltip"), "");
        String htmlTooltip = Objects.toString(request.getParameter("html-tooltip"), "");
        String classes = Objects.toString(request.getParameter("classes"), "");
        String pluginName = extractPluginNameFromIconSrc(symbol);
        String id = Objects.toString(request.getParameter("id"), "");

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("symbol", IconSet.getSymbol(symbol, title, tooltip, tooltip, classes, pluginName, id));

        return HttpResponses.okJSON(jsonObject);
    }
}
