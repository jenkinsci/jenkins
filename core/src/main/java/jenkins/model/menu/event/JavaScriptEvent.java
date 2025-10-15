package jenkins.model.menu.event;

import java.util.Map;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class JavaScriptEvent implements Event {

    private final Map<String, String> attributes;

    private final String javascriptUrl;

    private JavaScriptEvent(Map<String, String> attributes, String javascriptUrl) {
        this.attributes = attributes;
        this.javascriptUrl = javascriptUrl;
    }

    public static JavaScriptEvent of(Map<String, String> attributes, String javascriptUrl) {
        Jenkins jenkins = Jenkins.get();
        return new JavaScriptEvent(attributes, jenkins.getRootUrl() + javascriptUrl);
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
