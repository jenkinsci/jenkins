package hudson.tasks;

import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Result;
import hudson.util.FormFieldValidator;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
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

    public BuildTrigger(String childProjects) {
        this.childProjects = childProjects;
    }

    public BuildTrigger(List<Project> childProjects) {
        this(Project.toNameList(childProjects));
    }

    public String getChildProjectsValue() {
        return childProjects;
    }

    public List<Project> getChildProjects() {
        return Project.fromNameList(childProjects);
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) {
        if(build.getResult()== Result.SUCCESS) {
            for (Project p : getChildProjects()) {
                listener.getLogger().println("Triggering a new build of "+p.getName());
                p.scheduleBuild();
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

        public Publisher newInstance(StaplerRequest req) {
            return new BuildTrigger(req.getParameter("childProjects"));
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
                        Job job = Hudson.getInstance().getJob(projectName);
                        if(job==null) {
                            error("No such project '"+projectName+"'. Did you mean '"+
                                Project.findNearest(projectName).getName()+"'?");
                            return;
                        }
                        if(!(job instanceof Project)) {
                            error(projectName+" is not a software build");
                            return;
                        }
                    }

                    ok();
                }
            }.process();
        }
    }
}
