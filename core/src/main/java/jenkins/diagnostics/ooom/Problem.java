package jenkins.diagnostics.ooom;

import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.lazy.AbstractLazyLoadRunMap;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Look at build numbers in build.xml and compare them with build IDs (timestamps).
 *
 * When they are inconsistent (newer build timestamp-wise has an older build number),
 * it'll confuse the binary search in {@link AbstractLazyLoadRunMap}, so detect them and report them.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Problem {
    private final Job job;

    /**
     * Number of inconsistencies, which is the number of builds whose IDs
     * have to be messed around on disk to collect problems.
     */
    private int inconsistencies;

    private final XPathExpression xpath;

    /**
     * Scans the inconsistencies and return the information about it.
     *
     * @return null if no problems were found.
     */
    public static Problem find(Job j) {
        Problem p = new Problem(j);
        if (p.inconsistencies==0)   return null;
        return p;
    }

    private Problem(Job j) {
        this.job = j;

        try {
            xpath = XPathFactory.newInstance().newXPath().compile("/*/number/text()");
        } catch (XPathExpressionException e) {
            throw new AssertionError(e);
        }

        inspect();
    }

    /**
     * Equality is based on the job.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Problem problem = (Problem) o;

        return job.equals(problem.job);

    }

    @Override
    public int hashCode() {
        return job.hashCode();
    }

    /**
     * Simply report inconsistencies in the ordering.
     */
    protected void inspect() {
        Map<Integer, BuildPtr> builds = scan();

        final List<BuildPtr> byN = new ArrayList<BuildPtr>(builds.values());
        // this is already sorted by BuildPtr.n
        int i=0;
        for (BuildPtr b : byN) {
            b.posByN = i++;
        }

        final List<BuildPtr> byId = new ArrayList<BuildPtr>(byN);
        Collections.sort(byId, BuildPtr.BY_ID);
        i=0;
        for (BuildPtr b : byN) {
            b.posByID = i++;
        }

        for (i=0; i<byN.size()-1; i++) {
            BuildPtr l = byN.get(i);
            BuildPtr r = byN.get(i + 1);
            if (l.isInconsistentWith(r)) {
                LOGGER.warning("Inconsistent ordering: "+l+" vs "+r);
                inconsistencies++;
            }
        }

        class Picker {
            /**
             * Pretend that these builds are removed/fixed already.
             * Inconsistencies with these builds are not counted.
             */
            Set<BuildPtr> removed = new HashSet<BuildPtr>();

            void findBuildsToRemove() {
                while (true) {
                    BuildPtr b = pick();
                    if (b==null)
                        break;
                    removed.add(b);
                }
            }

            /**
             * Find the most inconsistent build, a build whose removal
             * would reduce the # of inconsistencies by the most.
             *
             * @return null if there's no more build to remove.
             */
            BuildPtr pick() {
                BuildPtr worst=null;
                int worstScore=-1;

                for (BuildPtr b : byN) {
                    if (removed.contains(b))
                        continue;

                    int score = score(b);
                    if (score>worstScore) {
                        worst = b;
                        worstScore = score;
                    }
                }

                return worst;
            }

            /**
             * Count the number of other builds the given build is inconsistent with.
             */
            private int score(BuildPtr b) {
                int i=0;
                for (BuildPtr a : byN) {
                    if (removed.contains(a))
                        continue;
                    if (a.isInconsistentWith(b))
                        i++;
                }
                return i;
            }
        }

        new Picker().findBuildsToRemove();
    }

    /**
     * Looks at the builds directory of the given job and builds up the full map of build number to its ID.
     */
    protected SortedMap<Integer,BuildPtr> scan() {
        LOGGER.fine("Inspecting "+job);

        SortedMap<Integer,BuildPtr> builds = new TreeMap<Integer,BuildPtr>();

        File[] files = job.getBuildDir().listFiles();
        if (files==null)    return builds;

        for (File build : files) {
            try {
                LOGGER.finer("Inspecting " + build);

                if (isInt(build.getName())) {
                    // if this is a number, then it must be a build number
                    String s = loadBuildNumberFromBuildXml(build);
                    if (!s.equals(build.getName())) {
                        LOGGER.warning(build+" contains build number "+s);
                        // this index is invalid.
                        if (build.delete()) {
                            // index should be a symlink, and if so we can just delete it without losing data.
                            LOGGER.info("Removed problematic index "+build);
                        } else {
                            // the deltion will fail if 'build' isn't just a symlink but an actual directory.
                            // That is good, as we don't want to delete any real data.
                            LOGGER.warning("Couldn't delete " + build);
                        }
                    }
                    continue;
                }


                if (isID(build.getName())) {
                    String bn = loadBuildNumberFromBuildXml(build);
                    if (bn==null) {
                        LOGGER.log(WARNING, "Failed to parse "+build);
                        continue;
                    }

                    int n;
                    try {
                        n = Integer.parseInt(bn);
                    } catch (NumberFormatException e) {
                        LOGGER.log(WARNING, "Expected number in " + build + " but found " + bn, e);
                        continue;
                    }

                    BuildPtr b = new BuildPtr(build,n);

                    BuildPtr o = builds.put(n, b);
                    if (o!=null)
                        LOGGER.warning("Multiple builds have the same number: "+o+" vs "+b);
                }
            } catch (XPathExpressionException e) {
                LOGGER.log(WARNING, "Failed to inspect "+build, e);
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to inspect "+build, e);
            }
        }

        return builds;
    }

    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isID(String s) {
        try {
            Run.ID_FORMATTER.get().parse(s);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private String loadBuildNumberFromBuildXml(File dir) throws XPathExpressionException, MalformedURLException {
        String systemId = new File(dir, "build.xml").toURI().toURL().toExternalForm();
        return (String)xpath.evaluate(new InputSource(systemId), XPathConstants.NODE);
    }

    private static final Logger LOGGER = Logger.getLogger(Problem.class.getName());
}
