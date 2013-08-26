package hudson.markup;

import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import org.owasp.html.Handler;
import org.owasp.html.HtmlSanitizer;
import org.owasp.html.HtmlStreamRenderer;
import org.owasp.html.PolicyFactory;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Policy definition based on OWASP AntiSamy MySpace policy.
 *
 * @author Kohsuke Kawaguchi
 * @see <a href="https://www.owasp.org/index.php/Category:OWASP_AntiSamy_Project#Stage_2_-_Choosing_a_base_policy_file">OWASP AntiSamy MySpace Policy</a>
 */
public class MyspacePolicy {
    public static final PolicyFactory POLICY_DEFINITION;

    private static final Pattern ONSITE_URL = Pattern.compile(
        "(?:[\\p{L}\\p{N}\\\\\\.\\#@\\$%\\+&;\\-_~,\\?=/!]+|\\#(\\w)+)");
    private static final Pattern OFFSITE_URL = Pattern.compile(
        "\\s*(?:(?:ht|f)tps?://|mailto:)[\\p{L}\\p{N}]"
        + "[\\p{L}\\p{N}\\p{Zs}\\.\\#@\\$%\\+&;:\\-_~,\\?=/!\\(\\)]*\\s*");
  
    private static final Predicate<String> ONSITE_OR_OFFSITE_URL
        = new Predicate<String>() {
          public boolean apply(String s) {
            return ONSITE_URL.matcher(s).matches()
                || OFFSITE_URL.matcher(s).matches();
          }
        };

    static {
        POLICY_DEFINITION = new HtmlPolicyBuilder2() {{
            allowAttributes("id","class","lang","title",
                    "alt","style","media","href","name","shape",
                    "border","cellpadding","cellspacing","colspan","rowspan",
                    "background","bgcolor","abbr","headers","charoff","char",
                    "aixs","nowrap","width","height","align","valign","scope",
                    "tabindex","disabled","readonly","accesskey","size",
                    "autocomplete","rows","cols").globally();

            disallowElements(
                    // I'm allowing iframe
                    "script","noscript",/*"iframe",*/"frameset","frame");

            tag("label",    "for");
            tag("form",     "action",ONSITE_OR_OFFSITE_URL,
                            "method");
            tag("button",   "value", "type");
            tag("input",    "maxlength","checked",
                            "src",ONSITE_OR_OFFSITE_URL,
                            "usemap",ONSITE_URL,
                            "type","value");
            tag("select",   "multiple");
            tag("option",   "value","label","selected");
            tag("textarea");
            tag("h1,h2,h3,h4,h5,h6,p,i,b,u,strong,em,small,big,pre,code,cite,samp,sub,sup,strike,center,blockquote");
            tag("hr,br,col");
            tag("font", "color", "face", "size");
            tag("a",        "nohref","rel");
            tag("style",    "type");
            tag("span,div");
            tag("img",      "src",ONSITE_OR_OFFSITE_URL,
                            "hspace","vspace");
            tag("iframe",   "src");
            tag("ul,ol,li,dd,dl,dt,thead,tbody,tfoot");
            tag("table",    "noresize");
            tag("td,th,tr");
            tag("colgroup", "span");
            tag("col",      "span");
            tag("fieldset,legend");
            allowStandardUrlProtocols();
        }}.toFactory();
    }

    public static void main(String[] args) throws IOException {
        // Fetch the HTML to sanitize.
        String html = "<a href='http://www.google.com/'>Google</a><img src='http://www.yahoo.com'>";
        // Set up an output channel to receive the sanitized HTML.
        HtmlStreamRenderer renderer = HtmlStreamRenderer.create(
                System.out,
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
        // Use the policy defined above to sanitize the HTML.
        HtmlSanitizer.sanitize(html, POLICY_DEFINITION.apply(renderer));
    }
}
