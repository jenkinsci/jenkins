package hudson.matrix;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;

import java.util.Comparator;
import java.util.List;
import jenkins.model.Jenkins;

/**
 * Add sorting for configurations {@link MatrixConfiguration}s of matrix job {@link MatrixProject}
 *
 * @since 1.437
 * @author Lucie Votypkova
 */
public abstract class MatrixConfigurationSorter extends AbstractDescribableImpl<MatrixConfigurationSorter> implements ExtensionPoint, Comparator<MatrixConfiguration> {

    public abstract String getDisplayName();
    
    /**
     *
     * @param axes
     *      list of chosen axes by user
     * @return
     *     true if the sorting of this axes by this sorter is possible (for example if the list of axes is not empty or contains axis which is needed for sorting.
     *     false if the sorting is impossible
     */
    public abstract boolean isSortingPossible(List<Axis> axes);
    
    /**
     *      
     * @return String message which will be displayed to user if sorting is impossible (method isSortingPossible(List<Axis> axes) return false)
     *     
     */
    public abstract String getErrorFormMessage();

    public static List<MatrixConfigurationSorter> all() {
        return Hudson.getInstance().getExtensionList(MatrixConfigurationSorter.class);
    }
}
