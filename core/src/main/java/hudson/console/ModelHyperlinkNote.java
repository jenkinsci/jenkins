package hudson.console;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Label;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.User;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

/**
 * {@link HyperlinkNote} that links to a {@linkplain ModelObject model object},
 * which in the UI gets rendered with context menu and etc.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.464
 */
public class ModelHyperlinkNote extends HyperlinkNote {
    public ModelHyperlinkNote(String url, int length) {
        super(url, length);
    }

    @Override
    protected String extraAttributes() {
        return " class='jenkins-table__link model-link model-link--float'";
    }

    public static String encodeTo(@NonNull User u) {
        return encodeTo(u, u.getDisplayName());
    }

    public static String encodeTo(User u, String text) {
        return encodeTo('/' + u.getUrl(), text);
    }

    public static String encodeTo(Item item) {
        return encodeTo(item, item.getFullDisplayName());
    }

    public static String encodeTo(Item item, String text) {
        return encodeTo('/' + item.getUrl(), text);
    }

    public static String encodeTo(Run r) {
        return encodeTo('/' + r.getUrl(), r.getDisplayName());
    }

    public static String encodeTo(Node node) {
        Computer c = node.toComputer();
        if (c != null) {
            return encodeTo("/" + c.getUrl(), node.getDisplayName());
        }
        String nodePath = node == Jenkins.get() ? "(built-in)" : node.getNodeName();
        return encodeTo("/computer/" + nodePath, node.getDisplayName());
    }

    /**
     * @since 2.230
     */
    public static String encodeTo(Label label) {
        return encodeTo("/" + label.getUrl(), label.getName());
    }

    @SuppressFBWarnings(value = "HSM_HIDING_METHOD", justification = "TODO needs triage")
    public static String encodeTo(String url, String text) {
        return HyperlinkNote.encodeTo(url, text, ModelHyperlinkNote::new);
    }

    @Extension @Symbol("hyperlinkToModels")
    public static class DescriptorImpl extends HyperlinkNote.DescriptorImpl {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Hyperlinks to models";
        }
    }

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ModelHyperlinkNote.class.getName());
}
