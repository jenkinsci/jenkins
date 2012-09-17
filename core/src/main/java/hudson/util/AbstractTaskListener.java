package hudson.util;

import hudson.console.HyperlinkNote;
import hudson.model.TaskListener;

import java.io.IOException;

/**
 * Partial default implementation of {@link TaskListener}
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractTaskListener implements TaskListener {
    public void hyperlink(String url, String text) throws IOException {
        annotate(new HyperlinkNote(url,text.length()));
        getLogger().print(text);
    }
}
