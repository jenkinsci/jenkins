package hudson.markup;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Writer;

/**
 * {@link MarkupFormatter} that treats the input as the raw html.
 * This is the backward compatible behaviour.
 *
 * @author Kohsuke Kawaguchi
 */
public class RawHtmlMarkupFormatter extends MarkupFormatter {

    final boolean disableSyntaxHighlighting;

    @DataBoundConstructor
    public RawHtmlMarkupFormatter(final boolean disableSyntaxHighlighting) {
        this.disableSyntaxHighlighting = disableSyntaxHighlighting;
    }

    public boolean isDisableSyntaxHighlighting() {
        return disableSyntaxHighlighting;
    }

    @Override
    public void translate(String markup, Writer output) throws IOException {
        output.write(markup);
    }

    public String getCodeMirrorMode() {
        return disableSyntaxHighlighting ? null : "htmlmixed";
    }

    public String getCodeMirrorConfig() {
        return "mode:'text/html'";
    }

    @Extension
    public static class DescriptorImpl extends MarkupFormatterDescriptor {
        @Override
        public String getDisplayName() {
            return "Raw HTML";
        }
    }

    public static MarkupFormatter INSTANCE = new RawHtmlMarkupFormatter(false);
}
