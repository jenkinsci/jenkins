package hudson.scm;

import hudson.model.Build;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * {@link ChangeLogParser} for Subversion.
 *
 * @author Kohsuke Kawaguchi
 */
public class SubversionChangeLogParser extends ChangeLogParser {
    public SubversionChangeLogSet parse(Build build, File changelogFile) throws IOException, SAXException {
        // http://svn.collab.net/repos/svn/trunk/subversion/svn/schema/

        Digester digester = new Digester();
        ArrayList<LogEntry> r = new ArrayList<LogEntry>();
        digester.push(r);

        digester.addObjectCreate("*/logentry", LogEntry.class);
        digester.addSetProperties("*/logentry");
        digester.addBeanPropertySetter("*/logentry/author","user");
        digester.addBeanPropertySetter("*/logentry/date");
        digester.addBeanPropertySetter("*/logentry/msg");
        digester.addSetNext("*/logentry","add");

        digester.addObjectCreate("*/logentry/paths/path", Path.class);
        digester.addSetProperties("*/logentry/paths/path");
        digester.addBeanPropertySetter("*/logentry/paths/path","value");
        digester.addSetNext("*/logentry/paths/path","addPath");

        digester.parse(changelogFile);

        return new SubversionChangeLogSet(build,r);
    }

}
