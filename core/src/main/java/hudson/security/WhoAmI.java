package hudson.security;

import hudson.Extension;
import hudson.Functions;
import hudson.model.Api;
import hudson.model.UnprotectedRootAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jenkins.util.MemoryReductionUtil;
import jenkins.model.Jenkins;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Expose the data needed for /whoAmI, so it can be exposed by Api.
 * 
 * @author Ryan Campbell
 *
 */
@Extension @Symbol("whoAmI")
@ExportedBean
public class WhoAmI implements UnprotectedRootAction {
    private static final Set<String> dangerousHeaders = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "cookie",
            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers#Authentication
            "authorization", "www-authenticate", "proxy-authenticate", "proxy-authorization"
    )));
    
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
    
    // @Exported removed due to leak of sessionId with some SecurityRealm
    public String getDetails() {
        return auth().getDetails() != null ? auth().getDetails().toString() : null;
    }
    
    // @Exported removed due to leak of sessionId with some SecurityRealm
    public String getToString() {
        return auth().toString();
    }

    private @NonNull Authentication auth() {
        return Jenkins.getAuthentication2();
    }

    @Exported
    public String[] getAuthorities() {
        if (auth().getAuthorities() == null) {
            return MemoryReductionUtil.EMPTY_STRING_ARRAY;
        }
        List <String> authorities = new ArrayList<>();
        for (GrantedAuthority a : auth().getAuthorities()) {
            authorities.add(a.getAuthority());
        }
        return authorities.toArray(new String[0]);
    }

    // Used by Jelly
    @Restricted(NoExternalUse.class)
    public boolean isHeaderDangerous(@NonNull String name) {
        return dangerousHeaders.contains(name.toLowerCase(Locale.ENGLISH));
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
