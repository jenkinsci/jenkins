package hudson.tasks;

import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Result;
import hudson.util.FormFieldValidator;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Triggers builds of other projects.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildTrigger extends Publisher {

    /**
     * Comma-separated list of other projects to be scheduled.
     */
    private String childProjects;

    /**
     * Threshold status to trigger other builds.
     *
     * For compatibility reasons, this field could be null, in which case
     * it should read as "SUCCESS".
     */
    private final Result threshold;

    @DataBoundConstructor
    public BuildTrigger(String childProjects, boolean evenIfUnstable) {
        this(childProjects,evenIfUnstable ? Result.UNSTABLE : Result.SUCCESS);
    }

    public BuildTrigger(String childProjects, Result threshold) {
        this.childProjects = childProjects;
        this.threshold = threshold;
    }

    public BuildTrigger(List<AbstractProject> childProjects, Result threshold) {
        this(Items.toNameList(childProjects),threshold);
    }

    public String getChildProjectsValue() {
        return childProjects;
    }

    public Result getThreshold() {
        if(threshold==null)
            return Result.SUCCESS;
        else
            return threshold;
    }

    public List<AbstractProject> getChildProjects() {
        return Items.fromNameList(childProjects,AbstractProject.class);
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) {
        if(!build.getResult().isWorseThan(getThreshold())) {
            PrintStream logger = listener.getLogger();
            for (AbstractProject p : getChildProjects()) {
                if(p.isDisabled()) {
                    logger.println(p.getName()+" is disabled. Triggering skiiped");
                    continue;
                }

                // this is not completely accurate, as a new build might be triggered
                // between these calls
                String name = p.getName()+" #"+p.getNextBuildNumber();
                if(!p.scheduleBuild()) {
                    logger.println("Triggering a new build of "+name);
                } else {
                    logger.println(name+" is already in the queue");
                }
            }
        }

        return true;
    }

    /**
     * Called from {@link Job#renameTo(String)} when a job is renamed.
     *
     * @return true
     *      if this {@link BuildTrigger} is changed and needs to be saved.
     */
    public boolean onJobRenamed(String oldName, String newName) {
        // quick test
        if(!childProjects.contains(oldName))
            return false;

        boolean changed = false;

        // we need to do this per string, since old Project object is already gone.
        String[] projects = childProjects.split(",");
        for( int i=0; i<projects.length; i++ ) {
            if(projects[i].trim().equals(oldName)) {
                projects[i] = newName;
                changed = true;
            }
        }

        if(changed) {
            StringBuilder b = new StringBuilder();
            for (String p : projects) {
                if(b.length()>0)    b.append(',');
                b.append(p);
            }
            childProjects = b.toString();
        }

        return changed;
    }

    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }


    public static final Descriptor<Publisher> DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<Publisher> {
        public DescriptorImpl() {
            super(BuildTrigger.class);
        }

        public String getDisplayName() {
            return "Build other projects";
        }

        public String getHelpFile() {
            return "/help/project-config/downstream.html";
        }

        public Publisher newInstance(StaplerRequest req) {
            return new BuildTrigger(
                req.getParameter("buildTrigger.childProjects"),
                req.getParameter("buildTrigger.evenIfUnstable")!=null
                );
        }

        /**
         * Form validation method.
         */
        public void doCheck( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
            new FormFieldValidator(req,rsp,true) {
                protected void check() throws IOException, ServletException {
                    String list = request.getParameter("value");

                    StringTokenizer tokens = new StringTokenizer(list,",");
                    while(tokens.hasMoreTokens()) {
                        String projectName = tokens.nextToken().trim();
                        Item item = Hudson.getInstance().getItemByFullName(projectName,Item.class);
                        if(item==null) {
                            error("No such project '"+projectName+"'. Did you mean '"+
                                AbstractProject.findNearest(projectName).getName()+"'?");
                            return;
                        }
                        if(!(item instanceof AbstractProject)) {
                            error(projectName+" is not buildable");
                            return;
                        }
                    }

                    ok();
                }
            }.process();
        }
    }
}
