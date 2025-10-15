package jenkins.model.menu.event;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class LinkEvent implements Event {

    private final String url;

    private final LinkEventType type;

    private LinkEvent(String url, LinkEventType type) {
        this.url = url;
        this.type = type;
    }

    public static LinkEvent of(String url) {
        return new LinkEvent(url, LinkEventType.GET);
    }

    public static LinkEvent of(String url, LinkEventType type) {
        return new LinkEvent(url, type);
    }

    @Exported
    public String getUrl() {
        return url;
    }

    @Exported
    public LinkEventType getType() {
        return type;
    }

    public enum LinkEventType {
        GET,
        POST
    }
}
