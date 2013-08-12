package hudson.tasks.junit;

import hudson.AbortException;
import hudson.Util;
import hudson.model.AbstractBuild;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TestResultUpdater {

    private static final Logger LOGGER = Logger.getLogger(TestResultUpdater.class.getName());

    private final AbstractBuild<?, ?> owner;

    public TestResultUpdater(final AbstractBuild<?, ?> owner) {

        this.owner = owner;
    }

    public void update(final TestResultAction action, final TestResult result) {

        final TestResult newResult = parseNew(result);

        if (newResult == null || newResult.getSuites().isEmpty()) return;

        LOGGER.info("Updating suits: " + newResult.getSuites());

        result.include(newResult);

        action.setResult(result, null);
    }

    private TestResult parseNew(final TestResult result) {

        final JUnitParser parser = new JUnitParser(getArchiver().isKeepLongStdio());

        final long started = System.currentTimeMillis();
        try {

            return parser.parse(getArchiver().getTestResults(), excludeAlreadyParsed(result), owner, null, null);
        } catch (AbortException ex) {
            // Thrown when there are no reports or no workspace witch is normal
            // at the beginning the build. This is also a signal that there are
            // no reports to update (already parsed was excluded and no new have
            // arrived so far).
            LOGGER.info("No new reports found.");
        } catch (InterruptedException ex) {

            System.out.println(ex.getMessage());
        } catch (IOException ex) {

            System.out.println(ex.getMessage());
        } finally {
            LOGGER.info(String.format("Parsing took %d ms", System.currentTimeMillis() - started));
        }

        return null;
    }

    private String excludeAlreadyParsed(final TestResult result) {

        if (result.getSuites().isEmpty()) return null;

        final String workspacePrefix = owner.getWorkspace().getRemote();

        final List<String> excludes = new ArrayList<String>(result.getSuites().size());
        for(final SuiteResult suite: result.getSuites()) {

            if (!suite.getFile().startsWith(workspacePrefix)) throw new AssertionError();
            excludes.add(suite.getFile().substring(workspacePrefix.length() + 1));
        }

        return Util.join(excludes, ", ");
    }

    private JUnitResultArchiver getArchiver() {

        return owner.getProject().getPublishersList().get(JUnitResultArchiver.class);
    }
}
