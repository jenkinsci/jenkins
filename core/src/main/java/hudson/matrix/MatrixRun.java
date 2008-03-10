package hudson.matrix;

import hudson.model.Build;

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

    @Override
    public MatrixConfiguration getParent() {// don't know why, but javac wants this
        return super.getParent();
    }
}
