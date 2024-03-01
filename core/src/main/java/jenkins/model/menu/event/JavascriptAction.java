package jenkins.model.menu.event;

public final class JavascriptAction implements Action {

    private final String url;

    public JavascriptAction(String url) {
        this.url = url;
    }

    public static JavascriptAction of(String url) {
        return new JavascriptAction(url);
    }

    public String getUrl() {
        return url;
    }
}
