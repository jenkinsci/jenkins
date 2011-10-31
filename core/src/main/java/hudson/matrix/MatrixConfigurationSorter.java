package hudson.matrix;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import java.util.List;
import jenkins.model.Jenkins;

/**
 * Add sorting for configurations {@link MatrixConfiguration}s of matrix job {@link MatrixProject}
 *
 * @since 1.437
 * @author Lucie Votypkova
 */
public abstract class MatrixConfigurationSorter implements ExtensionPoint, Describable<MatrixConfigurationSorter> {

    /**
     *
     * @param matrix configuration1, matrix configuration2
     *      The configurations that are compared.
     * @return
     *     int number for their comparing
     */
   public abstract int compare(MatrixConfiguration configuration1, MatrixConfiguration configuration2);
    
   
    public abstract String getDisplayName();
    
    /**
     *
     * @param List of chosen axes by user
     *      
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

    public Descriptor<MatrixConfigurationSorter> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
    
    public static List<MatrixConfigurationSorter> all() {
        return Hudson.getInstance().getExtensionList(MatrixConfigurationSorter.class);
    }
}
