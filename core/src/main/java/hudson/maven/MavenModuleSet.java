package hudson.maven;

import hudson.model.AbstractItem;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Project;
import hudson.model.Descriptor.FormException;
import hudson.util.CopyOnWriteMap;
import hudson.scm.SCM;
import hudson.scm.NullSCM;
import hudson.scm.SCMS;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;

/**
 * Group of {@link MavenModule}s.
 *
 * <p>
 * This corresponds to the group of Maven POMs that constitute a single
 * tree of projects. This group serves as the grouping of those related
 * modules.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenModuleSet extends AbstractItem implements TopLevelItem, ItemGroup<MavenModule> {
    /**
     * All {@link MavenModule}s.
     */
    transient /*final*/ Map<String,MavenModule> modules = new CopyOnWriteMap.Tree<String,MavenModule>();

    private SCM scm = new NullSCM();

    /**
     * Identifies {@link JDK} to be used.
     * Null if no explicit configuration is required.
     *
     * <p>
     * Can't store {@link JDK} directly because {@link Hudson} and {@link Project}
     * are saved independently.
     *
     * @see Hudson#getJDK(String)
     */
    private String jdk;

    /**
     * True to suspend any new builds in this module set.
     */
    private boolean disabled;

    public MavenModuleSet(String name) {
        super(name);
    }

    public String getUrlChildPrefix() {
        return "module";
    }

    public Hudson getParent() {
        return Hudson.getInstance();
    }

    public SCM getScm() {
        return scm;
    }

    public void setScm(SCM scm) {
        this.scm = scm;
    }

    public JDK getJDK() {
        return getParent().getJDK(jdk);
    }

    public synchronized void setJDK(JDK jdk) throws IOException {
        this.jdk = jdk.getName();
    }
    
    public Collection<MavenModule> getItems() {
        return modules.values();
    }

    public MavenModule getItem(String name) {
        return modules.get(name);
    }

    public Collection<MavenModule> getAllJobs() {
        return getItems();
    }


    public void onLoad(String name) throws IOException {
        super.onLoad(name);

        File modulesDir = new File(root,"modules");
        modulesDir.mkdirs(); // make sure it exists

        File[] subdirs = modulesDir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory();
            }
        });
        modules = new CopyOnWriteMap.Tree<String,MavenModule>();
        for (File subdir : subdirs) {
            try {
                MavenModule item = (MavenModule) Items.load(subdir);
                modules.put(item.getName(), item);
            } catch (IOException e) {
                e.printStackTrace(); // TODO: logging
            }
        }
    }

    public synchronized void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        try {
            disabled = req.getParameter("disable")!=null;
            jdk = req.getParameter("jdk");
            setScm(SCMS.parseSCM(req));
        } catch (FormException e) {
            throw new ServletException(e);
        }

        save();
        rsp.sendRedirect(".");
    }

    public TopLevelItemDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final TopLevelItemDescriptor DESCRIPTOR = new TopLevelItemDescriptor(MavenModuleSet.class) {
        public String getDisplayName() {
            return "Building a maven2 project";
        }

        public MavenModuleSet newInstance(String name) {
            return new MavenModuleSet(name);
        }
    };

    static {
        Items.XSTREAM.alias("maven2-module-set", MavenModule.class);
    }
}
