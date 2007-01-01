package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.scm.ChangeLogSet.Entry;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

/**
 * Encapsulates the file format of the changelog.
 *
 * Instances should be stateless, but
 * persisted as a part of {@link Build}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ChangeLogParser {
    public abstract ChangeLogSet<? extends Entry> parse(AbstractBuild build, File changelogFile) throws IOException, SAXException;
}
