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
    @DataBoundConstructor
    public RawHtmlMarkupFormatter() {
    }

    @Override
    public void translate(String markup, Writer output) throws IOException {
        output.write(markup);
    }

    @Extension
    public static class DescriptorImpl extends MarkupFormatterDescriptor {
        @Override
        public String getDisplayName() {
            return "Raw HTML";
        }
    }

    public static MarkupFormatter INSTANCE = new RawHtmlMarkupFormatter();
}
