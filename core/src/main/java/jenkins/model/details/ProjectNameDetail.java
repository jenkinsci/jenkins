package jenkins.model.details;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import hudson.model.Hudson;
import hudson.model.Job;
import java.util.Objects;

/**
 * Displays the full name of a project (if necessary)
 */
public class ProjectNameDetail extends Detail {

    public ProjectNameDetail(Actionable object) {
        super(object);
    }

    public @Nullable String getIconClassName() {
        if (getDisplayName() == null) {
            return null;
        }

        return "symbol-information-circle";
    }

    @Override
    public @Nullable String getDisplayName() {
        var it = (Job<?, ?>) getObject();

        if (Objects.equals(it.getFullName(), it.getFullDisplayName()) || it.getClass().getName().equals("MatrixConfiguration")) {
            return null;
        }

        boolean nested = it.getParent().getClass() != Hudson.class;
        String label = nested ? "Full project name" : "Project name";

        return label + ": " + it.getFullName();
    }
}
