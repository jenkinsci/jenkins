package hudson.scm;

import hudson.model.AbstractBuild;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

/**
 * {@link ChangeLogParser} for no SCM.
 * @author Kohsuke Kawaguchi
 */
public class NullChangeLogParser extends ChangeLogParser {
    public ChangeLogSet<? extends ChangeLogSet.Entry> parse(AbstractBuild build, File changelogFile) throws IOException, SAXException {
        return ChangeLogSet.createEmpty(build);
    }
}
