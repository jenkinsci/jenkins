package hudson.scm;

import hudson.model.AbstractBuild;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

/**
 * {@link ChangeLogParser} for CVS.
 * @author Kohsuke Kawaguchi
 */
public class CVSChangeLogParser extends ChangeLogParser {
    public CVSChangeLogSet parse(AbstractBuild build, File changelogFile) throws IOException, SAXException {
        return CVSChangeLogSet.parse(build,changelogFile);
    }
}
