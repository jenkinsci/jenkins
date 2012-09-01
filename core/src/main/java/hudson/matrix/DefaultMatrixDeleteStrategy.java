package hudson.matrix;

import hudson.Extension;

import java.io.IOException;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Default {@link MatrixDeleteStrategy} - deletes matrix build including all sub-builds if the build  
 * or keep whole build including sub-builds if the build should be kept.
 * 
 * @author vjuranek
 * @since 1.481
 *
 */
public class DefaultMatrixDeleteStrategy extends MatrixDeleteStrategy {
    
    @DataBoundConstructor
    public DefaultMatrixDeleteStrategy() {
        
    }
    
    public void doDelete(MatrixBuild b) throws MatrixDeleteException, IOException {
        b.checkPermission(b.DELETE);

        // We should not simply delete the build if it has been explicitly
        // marked to be preserved, or if the build should not be deleted
        // due to dependencies!
        String why = b.getWhyKeepLog();
        if (why!=null) {
            throw new MatrixDeleteException(hudson.model.Messages.Run_UnableToDelete(toString(),why));
        }
        
        List<MatrixRun> runs = b.getExactRuns();
        for(MatrixRun run : runs){
            why = run.getWhyKeepLog();
            if (why!=null) {
                throw new MatrixDeleteException(hudson.model.Messages.Run_UnableToDelete(toString(),why));
            }
            run.delete();
        }
        b.delete();
    }
    
    @Extension
    public static class DescriptorImpl extends MatrixDeleteStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return "Classic (delete or keep whole build)";
        }
    }

}
