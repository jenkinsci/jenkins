package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.scm.SubversionChangeLogSet.LogEntry;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.util.Digester2;
import hudson.util.IOException2;
import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * {@link ChangeLogParser} for Subversion.
 *
 * @author Kohsuke Kawaguchi
 */
public class SubversionChangeLogParser extends ChangeLogParser {
    public SubversionChangeLogSet parse(AbstractBuild build, File changelogFile) throws IOException, SAXException {
        // http://svn.collab.net/repos/svn/trunk/subversion/svn/schema/

        Digester digester = new Digester2();
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

        try {
            digester.parse(changelogFile);
        } catch (IOException e) {
            throw new IOException2("Failed to parse "+changelogFile,e);
        } catch (SAXException e) {
            throw new IOException2("Failed to parse "+changelogFile,e);
        }

        return new SubversionChangeLogSet(build,r);
    }

}
