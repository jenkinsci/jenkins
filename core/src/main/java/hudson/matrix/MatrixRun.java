package hudson.matrix;

import hudson.model.Build;
import hudson.maven.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Calendar;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.Ancestor;

/**
 * Execution of {@link MatrixConfiguration}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixRun extends Build<MatrixConfiguration,MatrixRun> {
    public MatrixRun(MatrixConfiguration job) throws IOException {
        super(job);
    }

    public MatrixRun(MatrixConfiguration job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MatrixRun(MatrixConfiguration project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public String getUpUrl() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null) {
            List<Ancestor> ancs = req.getAncestors();
            for( int i=1; i<ancs.size(); i++) {
                if(ancs.get(i).getObject()==this) {
                    if(ancs.get(i-1).getObject() instanceof MatrixBuild) {
                        return ancs.get(i-1).getUrl()+'/';
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    /**
     * Gets the {@link MatrixBuild} that has the same build number.
     *
     * @return
     *      null if no such build exists, which happens when the module build
     *      is manually triggered.
     */
    public MatrixBuild getParentBuild() {
        return getParent().getParent().getBuildByNumber(getNumber());
    }

    public String getDisplayName() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null) {
            List<Ancestor> ancs = req.getAncestors();
            for( int i=1; i<ancs.size(); i++) {
                if(ancs.get(i).getObject()==this) {
                    if(ancs.get(i-1).getObject() instanceof MatrixBuild) {
                        return getParent().getCombination().toCompactString(getParent().getParent().getAxes());
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    @Override
    public Map<String,String> getBuildVariables() {
        // pick up user axes
        return new HashMap<String,String>(getParent().getCombination());
    }

    /**
     * If the parent {@link MatrixRun} is kept, keep this record, too.
     */
    @Override
    public String getWhyKeepLog() {
        MatrixBuild pb = getParentBuild();
        if(pb!=null && pb.getWhyKeepLog()!=null)
            return hudson.maven.Messages.MavenBuild_KeptBecauseOfParent(pb);
        return super.getWhyKeepLog();
    }

    @Override
    public MatrixConfiguration getParent() {// don't know why, but javac wants this
        return super.getParent();
    }
}
