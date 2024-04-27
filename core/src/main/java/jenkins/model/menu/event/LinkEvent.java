package jenkins.model.menu.event;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class LinkEvent implements Event {

    private final String url;

    private LinkEvent(String url) {
        this.url = url;
    }

    public static LinkEvent of(String url) {
        return new LinkEvent(url);
    }

    @Exported
    public String getUrl() {
        return url;
    }
}
