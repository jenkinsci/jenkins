package hudson.markup;

import com.google.common.base.Throwables;
import org.junit.Assert;
import org.junit.Test;
import org.owasp.html.Handler;
import org.owasp.html.HtmlSanitizer;
import org.owasp.html.HtmlStreamRenderer;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class MyspacePolicyTest extends Assert {
    @Test
    public void testPolicy() {
        assertIntact("<a href='http://www.cloudbees.com'>CB</a>");
        assertIntact("<a href='relative/link'>relative</a>");
        assertIntact("<a href='mailto:kk&#64;kohsuke.org'>myself</a>");
        assertReject("javascript","<a href='javascript:alert(5)'>test</a>");

        assertIntact("<img src='http://www.cloudbees.com'>");
        assertIntact("<img src='relative/test.png'>");
        assertIntact("<img src='relative/test.png'>");
        assertReject("javascript","<img src='javascript:alert(5)'>");

        assertIntact("<b><i><u><strike>basic tag</strike></u></i></b>");
        assertIntact("<div><p>basic block tags</p></div>");

        assertIntact("<ul><li>1</li><li>2</li><li>3</li></ul>");
        assertIntact("<ol><li>x</li></ol>");
        assertIntact("<dl><dt>abc</dt><dd>foo</dd></dl>");
        assertIntact("<table><tr><th>header</th></tr><tr><td>something</td></tr></table>");
        assertIntact("<h1>title</h1><blockquote>blurb</blockquote>");

        assertIntact("<iframe src='nested'></iframe>");
        assertIntact("<iframe src='http://kohsuke.org'></iframe>");
        assertReject("javascript","<iframe src='javascript:foo'></iframe>");

        assertReject("script","<script>window.alert(5);</script>");
        assertReject("script","<script src='http://foo/evil.js'></script>");
        assertReject("script","<script src='relative.js'></script>");

        assertIntact("<style>H1 { display:none; }</style>");
        assertReject("link", "<link rel='stylesheet' type='text/css' href='http://www.microsoft.com/'>");
        assertIntact("<div style='background-color:white'>inline CSS</div>");
        assertIntact("<br><hr>");

        assertIntact("<form method='post' action='http://sun.com/'><input type='text' name='foo'><input type='password' name='pass'></form>");
    }

    private void assertIntact(String input) {
        input = input.replace('\'','\"');
        assertSanitize(input,input);
    }
    
    private void assertReject(String problematic, String input) {
        String out = sanitize(input);
        assertFalse(out, out.contains(problematic));
    }
    
    private void assertSanitize(String expected, String input) {
        assertEquals(expected,sanitize(input));
    }

    private String sanitize(String input) {
        StringBuilder buf = new StringBuilder();
        HtmlStreamRenderer renderer = HtmlStreamRenderer.create(
                buf,
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
                        throw new AssertionError(x);
                    }
                }
        );
        HtmlSanitizer.sanitize(input, MyspacePolicy.POLICY_DEFINITION.apply(renderer));
        return buf.toString();
    }
}
