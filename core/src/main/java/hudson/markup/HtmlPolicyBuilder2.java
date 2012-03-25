package hudson.markup;

import com.google.common.base.Predicate;
import org.owasp.html.HtmlPolicyBuilder;

import java.util.regex.Pattern;

/**
 * {@link HtmlPolicyBuilder} with additional
 * functions to simplify transcoding policy definition
 * from OWASP AntiSamy policy files.
 *
 * @author Kohsuke Kawaguchi
 */
class HtmlPolicyBuilder2 extends HtmlPolicyBuilder {
    public void tag(String names, Object... attributes) {
        String[] tags = names.split(",");
        for (int i=0; i<attributes.length; i++) {
            String attName = (String)attributes[i];
            if (i+1<attributes.length) {
                Object operand = attributes[i+1];
                if (operand instanceof Predicate) {
                    Predicate p = (Predicate) operand;
                    allowAttributes(attName).matching(p).onElements(tags);
                    i++;
                    continue;
                }

                if (operand instanceof Pattern) {
                    Pattern p = (Pattern) operand;
                    allowAttributes(attName).matching(p).onElements(tags);
                    i++;
                    continue;
                }
            }

            // operand-less
            allowAttributes(attName).onElements(tags);
        }
        allowElements(tags);
    }
}
