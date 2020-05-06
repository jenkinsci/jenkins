package hudson.cli;

import hudson.AbortException;
import jenkins.model.Jenkins;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.List;
import java.util.HashSet;

/**
 * CLI command, which helps delete job(s) or node(s).
 * @since TODO
 */

public abstract class DeleteCommand<T> extends CLICommand {

    @Restricted(NoExternalUse.class)
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")

    protected abstract void tryDelete(String name, Jenkins jenkins) throws Exception;

    protected void checkExists(T elementObject, String elementName, String elementType) throws Exception{
        if(elementObject == null) {
            throw new IllegalArgumentException("No such " + elementType + " '" + elementName + "'");
        }
        return;
    }

    protected int deleteElements(List<String> elements) throws Exception {

        boolean errorOccurred = false;
        final Jenkins jenkins = Jenkins.get();

        final HashSet<String> uniqueNames = new HashSet<>(elements);

        for (String elementName: uniqueNames) {

            try {
                tryDelete(elementName, jenkins);
            } catch (Exception e) {
                if(uniqueNames.size() == 1) {
                    throw e;
                }

                final String errorMsg = elementName + ": " + e.getMessage();
                stderr.println(errorMsg);
                errorOccurred = true;
                continue;
            }
        }

        if (errorOccurred) {
            throw new AbortException(CLI_LISTPARAM_SUMMARY_ERROR_TEXT);
        }
        return 0;
    }

}
