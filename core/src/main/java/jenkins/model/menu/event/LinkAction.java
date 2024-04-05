package jenkins.model.menu.event;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class LinkAction implements Action {

    private final String url;

    private LinkAction(String url) {
        this.url = url;
    }

    public static LinkAction of(String url) {
        return new LinkAction(url);
    }

    @Exported
    public String getUrl() {
        return url;
    }
}
