package hudson.cli;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.TopLevelItem;
import org.kohsuke.args4j.Argument;

import java.io.Serializable;

/**
 * Copies a job from CLI.
 * 
 * @author Kohsuke Kawaguchi
 */
public class CopyJobCommand extends CLICommand implements Serializable {
    @Override
    public String getShortDescription() {
        return "Copies a job";
    }

    @Argument(metaVar="SRC",usage="Name of the job to copy")
    public String src;

    @Argument(metaVar="DST",usage="Name of the new job to be created.",index=1)
    public String dst;

    protected int run() throws Exception {
        Hudson h = Hudson.getInstance();
        TopLevelItem s = h.getItem(src);
        if (s==null) {
            stderr.println("No such job '"+src+"' perhaps you meant "+ AbstractProject.findNearest(src)+"?");
            return -1;
        }
        if (h.getItem(dst)!=null) {
            stderr.println("Job '"+dst+"' already exists");
            return -1;
        }
        
        h.copy(s,dst);
        return 0;
    }
}

