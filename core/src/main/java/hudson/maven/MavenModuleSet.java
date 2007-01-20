package hudson.maven;

import hudson.model.AbstractItem;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.Items;
import hudson.util.CopyOnWriteMap;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

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
    private final String name;

    /**
     * All {@link MavenModule}s.
     */
    transient final Map<String,MavenModule> modules = new CopyOnWriteMap.Tree<String,MavenModule>();

    public MavenModuleSet(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return name;
    }

    public String getName() {
        return name;
    }

    public String getUrlChildPrefix() {
        return "module";
    }

    public Hudson getParent() {
        return Hudson.getInstance();
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
        modules.clear();
        for (File subdir : subdirs) {
            try {
                MavenModule item = (MavenModule) Items.load(subdir);
                modules.put(item.getName(), item);
            } catch (IOException e) {
                e.printStackTrace(); // TODO: logging
            }
        }
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
