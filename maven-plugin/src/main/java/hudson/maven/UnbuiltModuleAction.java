package hudson.maven;

import hudson.model.Action;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Action which remembers all module which have not been built since the last successful build
 * though they should have been, because they have SCM changes since then.
 *
 * See JENKINS-5764
 * 
 * @author kutzi
 */
public class UnbuiltModuleAction implements Action {

    private List<ModuleName> moduleNames = new ArrayList<ModuleName>();
    
    public void addUnbuiltModule(ModuleName moduleName) {
        this.moduleNames.add(moduleName);
    }
    
    public boolean removeUnbuildModule(ModuleName moduleName) {
        return this.moduleNames.remove(moduleName);
    }
    
    public Collection<ModuleName> getUnbuildModules() {
        return this.moduleNames;
    }
    
    /**
     * {@inheritDoc}
     */
    public String getIconFileName() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public String getDisplayName() {
        return "Unbuilt Modules";
    }

    /**
     * {@inheritDoc}
     */
    public String getUrlName() {
        return null;
    }

}
