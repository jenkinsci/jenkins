package hudson.markup;

import com.google.common.base.Throwables;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.owasp.html.Handler;
import org.owasp.html.HtmlSanitizer;
import org.owasp.html.HtmlStreamRenderer;

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
        HtmlStreamRenderer renderer = HtmlStreamRenderer.create(
                output,
                // Receives notifications on a failure to write to the output.
                new Handler<IOException>() {
                    public void handle(IOException ex) {
                        Throwables.propagate(ex);  // System.out suppresses IOExceptions
                    }
                },
                // Our HTML parser is very lenient, but this receives notifications on
                // truly bizarre inputs.
                new Handler<String>() {
                    public void handle(String x) {
                        throw new Error(x);
                    }
                }
        );
        // Use the policy defined above to sanitize the HTML.
        HtmlSanitizer.sanitize(markup, MyspacePolicy.POLICY_DEFINITION.apply(renderer));
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

    public static final MarkupFormatter INSTANCE = new RawHtmlMarkupFormatter(false);
}
