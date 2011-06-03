package hudson.maven;

import hudson.model.Action;

/**
 * Action signalling that this {@link MavenModuleSet} needs a full build
 * on the next run even if it's marked as an incremental build.
 *  
 * @author kutzi
 */
public class NeedsFullBuildAction implements Action {

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}
