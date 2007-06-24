package hudson.matrix;

import hudson.model.Build;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

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

    public MatrixRun(MatrixConfiguration project, File buildDir) throws IOException {
        super(project, buildDir);
    }


    public String getDisplayName() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if(req!=null) {
            List<Ancestor> ancs = req.getAncestors();
            for( int i=1; i<ancs.size(); i++) {
                if(ancs.get(i).getObject()==this) {
                    if(ancs.get(i-1).getObject() instanceof MatrixBuild) {
                        return getParent().getCombination().toString();
                    }
                }
            }
        }
        return super.getDisplayName();
    }

    @Override
    public Map<String,String> getBuildVariables() {
        AxisList axes = getParent().getParent().getAxes();

        // pick up user axes
        Map<String,String> r = new HashMap<String,String>();
        for (Entry<String,String> e : getParent().getCombination().entrySet()) {
            if(!axes.find(e.getKey()).isSystem())
                r.put(e.getKey(),e.getValue());
        }
        return r;
    }

    @Override
    public MatrixConfiguration getParent() {// don't know why, but javac wants this
        return super.getParent();
    }
}
