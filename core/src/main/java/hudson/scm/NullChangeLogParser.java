package hudson.scm;

import hudson.model.Build;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

/**
 * {@link ChangeLogParser} for no SCM.
 * @author Kohsuke Kawaguchi
 */
public class NullChangeLogParser extends ChangeLogParser {
    public ChangeLogSet<? extends ChangeLogSet.Entry> parse(Build build, File changelogFile) throws IOException, SAXException {
        return ChangeLogSet.EMPTY;
    }
}
