package jenkins.model.labels;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.Label;
import hudson.util.FormValidation;

/**
 * Plugins may want to contribute additional restrictions on the use of specific labels for specific context items.
 * This extension point allows such restrictions.
 *
 * @since 2.243
 */
public interface LabelValidator extends ExtensionPoint {

    /**
     * Validates the use of a label within a particular context.
     * <p>
     * Note that "OK" responses (and any text/markup that may be set on them) will be ignored. Only warnings and errors
     * are taken into account, and aggregated across all validators.
     *
     * @param item  The context item to be restricted by the label.
     * @param label The label that the job wants to restrict itself to.
     * @return The validation result.
     */
    @NonNull
    FormValidation check(@NonNull Item item, @NonNull Label label);

}
