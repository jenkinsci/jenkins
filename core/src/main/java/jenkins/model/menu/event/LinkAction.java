package jenkins.model.menu.event;

public final class LinkAction implements Action {

    private final String url;

    public LinkAction(String url) {
        this.url = url;
    }

    public static LinkAction of(String url) {
        return new LinkAction(url);
    }

    public String getUrl() {
        return url;
    }
}
