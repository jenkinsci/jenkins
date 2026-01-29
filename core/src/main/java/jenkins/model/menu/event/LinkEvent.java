package jenkins.model.menu.event;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * A link to be added to the app-bar.
 */
@ExportedBean
public final class LinkEvent implements Event {

    private final String url;

    private final LinkEventType type;

    private LinkEvent(String url, LinkEventType type) {
        this.url = url;
        this.type = type;
    }

    /**
     * A simple link
     */
    public static LinkEvent of(String url) {
        return new LinkEvent(url, LinkEventType.GET);
    }

    /**
     * A link with the ability to control if it's a get or a post.
     */
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

    /**
     * Types of links
     */
    public enum LinkEventType {
        GET,
        POST
    }
}
