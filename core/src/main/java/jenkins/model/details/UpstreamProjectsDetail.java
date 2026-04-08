package jenkins.model.details;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.AbstractProject;
import hudson.model.Actionable;
import hudson.model.Item;
import java.util.List;

/**
 * Displays upstream projects of a project (if any)
 */
public class UpstreamProjectsDetail extends Detail {

    public UpstreamProjectsDetail(Actionable object) {
        super(object);
    }

    public @Nullable String getIconClassName() {
        if (getProjects().isEmpty()) {
            return null;
        }

        return "symbol-arrow-up-circle-outline plugin-ionicons-api";
    }

    @Override
    public @Nullable String getDisplayName() {
        int projectSize = getProjects().size();

        if (projectSize == 1) {
            return "1 upstream project";
        }

        return projectSize + " upstream projects";
    }

    public List<AbstractProject> getProjects() {
        if (!(getObject() instanceof AbstractProject)) {
            return List.of();
        }

        List<AbstractProject> projects = ((AbstractProject) getObject()).getUpstreamProjects();
        return projects.stream().filter(e -> e.hasPermission(Item.READ)).toList();
    }
}
