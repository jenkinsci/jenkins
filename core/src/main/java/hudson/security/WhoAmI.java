package hudson.security;

import hudson.Extension;
import hudson.Functions;
import hudson.model.Api;
import hudson.model.UnprotectedRootAction;

import java.util.ArrayList;
import java.util.List;

import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Expose the data needed for /whoAmI, so it can be exposed by Api.
 * 
 * @author Ryan Campbell
 *
 */
@Extension @Symbol("whoAmI")
@ExportedBean
public class WhoAmI implements UnprotectedRootAction {
    
    public Api getApi() {
        return new Api(this);
    }
    
    @Exported
    public String getName() {
        return auth().getName();
    }
    
    @Exported
    public boolean isAuthenticated() {
        return auth().isAuthenticated();
    }
    
    @Exported
    public boolean isAnonymous() {
        return Functions.isAnonymous();
    }
    
    @Exported
    public String getDetails() {
        return auth().getDetails() != null ? auth().getDetails().toString() : null;
    }
    
    @Exported
    public String getToString() {
        return auth().toString();
    }

    private Authentication auth() {
        return Jenkins.getAuthentication();
    }

    @Exported
    public String[] getAuthorities() {
        if (auth().getAuthorities() == null) {
            return new String[0];
        }
        List <String> authorities = new ArrayList<String>();
        for (GrantedAuthority a : auth().getAuthorities()) {
            authorities.add(a.getAuthority());
        }
        return (String[]) authorities.toArray(new String[authorities.size()]);
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Who Am I";
    }

    @Override
    public String getUrlName() {
        return "whoAmI";
    }
}
