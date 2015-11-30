package jenkins.model;

import hudson.Extension;
import hudson.model.Items;
import hudson.model.TopLevelItem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;


@Extension
public class DiskItemLoader extends TopLevelItemLoader {

    public Collection<TopLevelItem> load(Jenkins jenkins) throws IOException {
        Collection<TopLevelItem> items = new ArrayList<TopLevelItem>();
        File[] subdirs = getProjectDir(jenkins).listFiles();
        for (final File subdir : subdirs) {
            if(Items.getConfigFile(subdir).exists()) {
                TopLevelItem item = (TopLevelItem) Items.load(jenkins, subdir);
                items.add(item);
            }
        }
        return items;
    }
    private File getProjectDir(Jenkins jenkins) throws IOException {
        File projectsDir = new File(jenkins.root,"jobs");
        if(!projectsDir.getCanonicalFile().isDirectory() && !projectsDir.mkdirs()) {
            if(projectsDir.exists())
                throw new IOException(projectsDir+" is not a directory");
            throw new IOException("Unable to create "+projectsDir+"\nPermission issue? Please create this directory manually.");
        }
        return projectsDir;
    }

}
