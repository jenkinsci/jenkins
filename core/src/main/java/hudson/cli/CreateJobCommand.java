package hudson.cli;

import hudson.model.Hudson;
import org.kohsuke.args4j.Argument;

/**
 * Creates a new job by reading stdin as a configuration XML file.
 * 
 * @author Kohsuke Kawaguchi
 */
public class CreateJobCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return "Creates a new job by reading stdin as a configuration XML file";
    }

    @Argument(metaVar="NAME",usage="Name of the job to create")
    public String name;

    protected int run() throws Exception {
        Hudson h = Hudson.getInstance();
        if (h.getItem(name)!=null) {
            stderr.println("Job '"+name+"' already exists");
            return -1;
        }

        h.createProjectFromXML(name,stdin);
        return 0;
    }
}


