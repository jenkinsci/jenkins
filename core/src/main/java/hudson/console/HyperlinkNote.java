package hudson.console;

import hudson.Extension;
import hudson.MarkupText;
import hudson.model.Hudson;

/**
 * Turns a text into a hyperlink by specifying the URL separately.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.362
 */
public class HyperlinkNote extends ConsoleNote {
    /**
     * If this starts with '/', it's interpreted as a path within the context path.
     */
    private final String url;
    private final int length;

    public HyperlinkNote(String url, int length) {
        this.url = url;
        this.length = length;
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        String url = this.url;
        if (url.startsWith("/"))
            url = Hudson.getInstance().getRootUrl()+url.substring(1);
        text.addHyperlink(charPos,charPos+length,url);
        return null;
    }

    @Extension
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        public String getDisplayName() {
            return "Hyperlinks";
        }
    }
}
