package jenkins.diagnostics.ooom;

import hudson.AbortException;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.lazy.AbstractLazyLoadRunMap;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
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
    public final Job job;

    /**
     * A smallest set of builds whose removals would correct the order
     * inconsistency problem.
     */
    private final Set<BuildPtr> offenders = new TreeSet<BuildPtr>();

    /**
     * Scans the inconsistencies and return the information about it.
     *
     * @return null if no problems were found.
     */
    public static Problem find(Job j) {
        Problem p = new Problem(j);
        if (p.countInconsistencies()==0)   return null;
        return p;
    }

    private Problem(Job j) {
        this.job = j;
        new Inspector().inspect();
    }

    public Set<BuildPtr> getOffenders() {
        return Collections.unmodifiableSet(offenders);
    }

    /**
     * Number of inconsistencies, which is the number of builds whose IDs
     * have to be messed around on disk to collect problems.
     */
    public int countInconsistencies() {
        return offenders.size();
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

    @Override
    public String toString() {
        return job.getFullDisplayName()+" "+ Util.join(offenders);
    }

    public void fix(TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("Fixing problems in "+job.getFullDisplayName());
        for (BuildPtr o : offenders) {
            o.fix(listener);
        }

        if (job instanceof AbstractProject) {
            // let all the current references go and build a new one
            AbstractProject a = (AbstractProject) job;
            a._getRuns().purgeCache();
        }
    }

    /**
     * Finds the problems and builds up the data model of {@link Problem}.
     */
    class Inspector {
        /**
         * All the builds sorted by their {@link BuildPtr#n}
         */
        private List<BuildPtr> byN;
        /**
         * All the builds sorted by their {@link BuildPtr#id}
         */
        private List<BuildPtr> byId;

        private final XPathExpression xpath;

        Inspector() {
            try {
                xpath = XPathFactory.newInstance().newXPath().compile("/*/number/text()");
            } catch (XPathExpressionException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Simply report inconsistencies in the ordering.
         */
        protected void inspect() {
            Map<Integer, BuildPtr> builds = scan();

            byN = new ArrayList<BuildPtr>(builds.values());
            // this is already sorted by BuildPtr.n
            int i=0;
            for (BuildPtr b : byN) {
                b.posByN = i++;
            }

            byId = new ArrayList<BuildPtr>(byN);
            Collections.sort(byId, BuildPtr.BY_ID);
            i=0;
            for (BuildPtr b : byId) {
                b.posByID = i++;
            }

            while (true) {
                BuildPtr b = pick();
                if (b==null)
                    break;
                offenders.add(b);
            }
        }

        /**
         * Find the most inconsistent build, a build whose removal
         * would reduce the # of inconsistencies by the most.
         *
         * This process takes {@link #offenders} into account.
         *
         * @return null if there's no more build to remove.
         */
        private BuildPtr pick() {
            BuildPtr worst=null;
            int worstScore=0;

            for (BuildPtr b : byN) {
                if (offenders.contains(b))
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
         * Count the number of other builds the given build is inconsistent with,
         * excluding inconsistencies with {@link #offenders} (since those inconsistencies
         * are already effectively resolved by fixing offenders.)
         */
        private int score(BuildPtr b) {
            int i=0;
            for (BuildPtr a : byN) {
                if (offenders.contains(a))
                    continue;
                if (a.isInconsistentWith(b))
                    i++;
            }
            return i;
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

                        BuildPtr b = new BuildPtr(job,build,n);

                        BuildPtr o = builds.put(n, b);
                        if (o!=null)
                            LOGGER.warning("Multiple builds have the same number: "+o+" vs "+b);
                    }
                } catch (XPathExpressionException e) {
                    LOGGER.log(WARNING, "Failed to inspect "+build, e);
                } catch (AbortException e) {
                    LOGGER.log(WARNING, "Failed to inspect "+build+": "+e.getMessage());
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

        private String loadBuildNumberFromBuildXml(File dir) throws XPathExpressionException, IOException {
            File buildXml = new File(dir, "build.xml");
            if (!buildXml.exists())
                throw new AbortException(buildXml+" doesn't exist");
            String systemId = buildXml.toURI().toURL().toExternalForm();
            return (String)xpath.evaluate(new InputSource(systemId), XPathConstants.STRING);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Problem.class.getName());
}
