package hudson.console;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.NonNull;

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
        return " class='model-link'";
    }

    public static String encodeTo(@NonNull User u) {
        return encodeTo(u,u.getDisplayName());
    }

    public static String encodeTo(User u, String text) {
        return encodeTo('/'+u.getUrl(),text);
    }

    public static String encodeTo(Item item) {
        return encodeTo(item,item.getFullDisplayName());
    }

    public static String encodeTo(Item item, String text) {
        return encodeTo('/'+item.getUrl(),text);
    }

    public static String encodeTo(Run r) {
        return encodeTo('/'+r.getUrl(),r.getDisplayName());
    }

    public static String encodeTo(Node node) {
        Computer c = node.toComputer();
        if (c != null) {
            return encodeTo("/" + c.getUrl(), node.getDisplayName());
        }
        String nodePath = node == Jenkins.get() ? "(master)" : node.getNodeName();
        return encodeTo("/computer/" + nodePath, node.getDisplayName());
    }

    /**
     * @since 2.230
     */
    public static String encodeTo(Label label) {
        return encodeTo("/" + label.getUrl(), label.getName());
    }

    public static String encodeTo(String url, String text) {
        return HyperlinkNote.encodeTo(url, text, ModelHyperlinkNote::new);
    }

    @Extension @Symbol("hyperlinkToModels")
    public static class DescriptorImpl extends HyperlinkNote.DescriptorImpl {
        public String getDisplayName() {
            return "Hyperlinks to models";
        }
    }
    
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ModelHyperlinkNote.class.getName());
}
