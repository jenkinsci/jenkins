package jenkins.model.menu.event;

import java.util.Map;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * When the event is rendered the javascriptUrl will be loaded.
 * Attributes are attached to the button or link as data-attributes.
 */
@ExportedBean
@Restricted(Beta.class)
public final class JavaScriptEvent implements Event {

    private final Map<String, String> attributes;

    private final String javascriptUrl;

    private JavaScriptEvent(Map<String, String> attributes, String javascriptUrl) {
        this.attributes = attributes;
        this.javascriptUrl = javascriptUrl;
    }

    /**
     * Create a JavaScriptEvent.
     * @param attributes attributes to add to the element as data-attributes.
     * @param javascriptUrl the script to load relative from the jenkins root url.
     * @return the event
     */
    public static JavaScriptEvent of(Map<String, String> attributes, String javascriptUrl) {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        String prefix = (req != null ? req.getContextPath() : "") + Jenkins.RESOURCE_PATH + "/";
        return new JavaScriptEvent(attributes, prefix + javascriptUrl);
    }

    /**
     * The attributes to add to the element as data-attributes.
     * e.g. type, or testid.
     */
    @Exported
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * The URL of the JavaScript to load.
     */
    @Exported
    public String getJavascriptUrl() {
        return javascriptUrl;
    }
}
