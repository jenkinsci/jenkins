package jenkins.model.menu.event;

import java.util.Map;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class JavaScriptAction implements Action {

    private final Map<String, String> attributes;

    private final String javascriptUrl;

    private JavaScriptAction(Map<String, String> attributes, String javascriptUrl) {
        this.attributes = attributes;
        this.javascriptUrl = javascriptUrl;
    }

    public static JavaScriptAction of(Map<String, String> attributes, String javascriptUrl) {
        Jenkins jenkins = Jenkins.get();
        return new JavaScriptAction(attributes, jenkins.getRootUrl() + javascriptUrl);
    }

    @Exported
    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Exported
    public String getJavascriptUrl() {
        return javascriptUrl;
    }
}
