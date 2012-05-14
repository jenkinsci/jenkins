package hudson.maven;

import hudson.model.Action;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Action which remembers all module which have been changed since the last successful build.
 * When a new incremental build is triggered, all of this modules should be rebuilt, too.
 *
 * See JENKINS-13758
 * 
 * @author paux
 */
public class ChangedModulesAction implements Action {

    private Set<ModuleName> moduleNames = new HashSet<ModuleName>();
    
    public Collection<ModuleName> getChangedModules() {
        return this.moduleNames;
    }
    
    public void addChangedModules(Set<ModuleName> changedModules) {
	moduleNames.addAll(changedModules);
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
        return "Changed Modules";
    }

    /**
     * {@inheritDoc}
     */
    public String getUrlName() {
        return null;
    }
    
    public ChangedModulesAction clone() {
        ChangedModulesAction clone = new ChangedModulesAction();
        clone.addChangedModules(moduleNames);
        return clone;
    }


}
