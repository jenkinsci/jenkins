package jenkins.install;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.lang.StringUtils.defaultIfBlank;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.BulkChange;
import hudson.Extension;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.PageDecorator;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.model.User;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.SecurityRealm;
import hudson.security.csrf.DefaultCrumbIssuer;
import hudson.util.HttpResponses;
import hudson.util.PluginServletFilter;
import hudson.util.VersionNumber;
import java.io.File;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;

import jenkins.model.Jenkins;
import jenkins.security.s2m.AdminWhitelistRule;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * A Jenkins instance used during first-run to provide a limited set of services while
 * initial installation is in progress
 * 
 * @since 2.0
 */
@Restricted(NoExternalUse.class)
@Extension
public class SetupWizard extends PageDecorator {
    /**
     * The security token parameter name
     */
    public static String initialSetupAdminUserName = "admin";

    private static final Logger LOGGER = Logger.getLogger(SetupWizard.class.getName());
    
    /**
     * Used to determine if this was a new install (vs. an upgrade, restart, or otherwise)
     */
    private static boolean isUsingSecurityToken = false;

    /**
     * Initialize the setup wizard, this will process any current state initializations
     */
    /*package*/ void init(boolean newInstall) throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.getInstance();
        
        if(newInstall) {
            // this was determined to be a new install, don't run the update wizard here
            setCurrentLevel(Jenkins.getVersion());
            
            // Create an admin user by default with a 
            // difficult password
            FilePath iapf = getInitialAdminPasswordFile();
            if(jenkins.getSecurityRealm() == null || jenkins.getSecurityRealm() == SecurityRealm.NO_AUTHENTICATION) { // this seems very fragile
                BulkChange bc = new BulkChange(jenkins);
                try{
                    HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
                    jenkins.setSecurityRealm(securityRealm);
                    String randomUUID = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ENGLISH);
    
                    // create an admin user
                    securityRealm.createAccount(SetupWizard.initialSetupAdminUserName, randomUUID);
    
                    // JENKINS-33599 - write to a file in the jenkins home directory
                    // most native packages of Jenkins creates a machine user account 'jenkins' to run Jenkins,
                    // and use group 'jenkins' for admins. So we allow groups to read this file
                    iapf.touch(System.currentTimeMillis());
                    iapf.chmod(0640);
                    iapf.write(randomUUID + System.lineSeparator(), "UTF-8");
                    
    
                    // Lock Jenkins down:
                    FullControlOnceLoggedInAuthorizationStrategy authStrategy = new FullControlOnceLoggedInAuthorizationStrategy();
                    authStrategy.setAllowAnonymousRead(false);
                    jenkins.setAuthorizationStrategy(authStrategy);
    
                    // Shut down all the ports we can by default:
                    jenkins.setSlaveAgentPort(-1); // -1 to disable
    
                    // require a crumb issuer
                    jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
    
                    // set master -> slave security:
                    jenkins.getInjector().getInstance(AdminWhitelistRule.class)
                        .setMasterKillSwitch(false);
                
                    jenkins.save(); // !!
                    bc.commit();
                } finally {
                    bc.abort();
                }
            }
    
            if(iapf.exists()) {
                String setupKey = iapf.readToString().trim();
                String ls = System.lineSeparator();
                LOGGER.info(ls + ls + "*************************************************************" + ls
                        + "*************************************************************" + ls
                        + "*************************************************************" + ls
                        + ls
                        + "Jenkins initial setup is required. An admin user has been created and "
                        + "a password generated." + ls
                        + "Please use the following password to proceed to installation:" + ls
                        + ls
                        + setupKey + ls
                        + ls
                        + "This may also be found at: " + iapf.getRemote() + ls
                        + ls
                        + "*************************************************************" + ls
                        + "*************************************************************" + ls
                        + "*************************************************************" + ls);
            }
            
            try {
                PluginServletFilter.addFilter(FORCE_SETUP_WIZARD_FILTER);
                isUsingSecurityToken = true;
            } catch (ServletException e) {
                throw new RuntimeException("Unable to add PluginServletFilter for the SetupWizard", e);
            }
        }
        
        try {
            // Make sure plugin metadata is up to date
            UpdateCenter.updateDefaultSite();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e.getMessage(), e);
        }
    }
    
    /**
     * Indicates a generated password should be used - e.g. this is a new install, no security realm set up
     */
    public boolean isUsingSecurityToken() {
        try {
            return isUsingSecurityToken // only ever show this if using the security token
                    && !Jenkins.getInstance().getInstallState().isSetupComplete()
                    && getInitialAdminPasswordFile().exists();
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
    
    /**
     * Called during the initial setup to create an admin user
     */
    public void doCreateAdminUser(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins j = Jenkins.getInstance();
        j.checkPermission(Jenkins.ADMINISTER);
        
        // This will be set up by default. if not, something changed, ok to fail
        HudsonPrivateSecurityRealm securityRealm = (HudsonPrivateSecurityRealm)j.getSecurityRealm();
        
        User admin = securityRealm.getUser(SetupWizard.initialSetupAdminUserName);
        try {
            if(admin != null) {
                admin.delete(); // assume the new user may well be 'admin'
            }
            
            User u = securityRealm.createAccountByAdmin(req, rsp, "/jenkins/install/SetupWizard/setupWizardFirstUser.jelly", req.getContextPath() + "/");
            if (u != null) {
                if(admin != null) {
                    admin = null;
                }
                
                // Success! Delete the temporary password file:
                try {
                    getInitialAdminPasswordFile().delete();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
                
                InstallUtil.proceedToNextStateFrom(InstallState.CREATE_ADMIN_USER);
                
                // ... and then login
                Authentication a = new UsernamePasswordAuthenticationToken(u.getId(),req.getParameter("password1"));
                a = securityRealm.getSecurityComponents().manager.authenticate(a);
                SecurityContextHolder.getContext().setAuthentication(a);
            }
        } finally {
            if(admin != null) {
                admin.save(); // recreate this initial user if something failed
            }
        }
    }

    /*package*/ void setCurrentLevel(VersionNumber v) throws IOException {
        FileUtils.writeStringToFile(getUpdateStateFile(), v.toString());
    }
    
    /**
     * File that captures the state of upgrade.
     *
     * This file records the version number that the installation has upgraded to.
     */
    /*package*/ static File getUpdateStateFile() {
        return new File(Jenkins.getInstance().getRootDir(),"jenkins.install.UpgradeWizard.state");
    }
    
    /**
     * What is the version the upgrade wizard has run the last time and upgraded to?.
     * If {@link #getUpdateStateFile()} is missing, presumes the baseline is 1.0
     * @return Current baseline. {@code null} if it cannot be retrieved.
     */
    @Restricted(NoExternalUse.class)
    @CheckForNull
    public VersionNumber getCurrentLevel() {
        VersionNumber from = new VersionNumber("1.0");
        File state = getUpdateStateFile();
        if (state.exists()) {
            try {
                from = new VersionNumber(defaultIfBlank(readFileToString(state), "1.0").trim());
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Cannot read the current version file", ex);
                return null;
            }
        }
        return from;
    }
    
    /**
     * Returns the initial plugin list in JSON format
     */
    @Restricted(DoNotUse.class) // WebOnly
    public HttpResponse doPlatformPluginList() throws IOException {
        jenkins.install.SetupWizard setupWizard = Jenkins.getInstance().getSetupWizard();
        if (setupWizard != null) {
            if (InstallState.UPGRADE.equals(Jenkins.getInstance().getInstallState())) {
                JSONArray initialPluginData = getPlatformPluginUpdates();
                if(initialPluginData != null) {
                    return HttpResponses.okJSON(initialPluginData);
                }
            } else {
                JSONArray initialPluginData = getPlatformPluginList();
                if(initialPluginData != null) {
                    return HttpResponses.okJSON(initialPluginData);
                }
            }
        }
        return HttpResponses.okJSON();
    }
    
    /**
     * Provides the list of platform plugin updates from the last time
     * the upgrade was run.
     * @return {@code null} if the version range cannot be retrieved.
     */
    @CheckForNull
    public JSONArray getPlatformPluginUpdates() {
        final VersionNumber version = getCurrentLevel();
        if (version == null) {
            return null;
        }
        return getPlatformPluginsForUpdate(version, Jenkins.getVersion());
    }
    
    /**
     * Gets the suggested plugin list from the update sites, falling back to a local version
     * @return JSON array with the categorized plugon list
     */
    @CheckForNull
    /*package*/ JSONArray getPlatformPluginList() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        JSONArray initialPluginList = null;
        updateSiteList: for (UpdateSite updateSite : Jenkins.getInstance().getUpdateCenter().getSiteList()) {
            String updateCenterJsonUrl = updateSite.getUrl();
            String suggestedPluginUrl = updateCenterJsonUrl.replace("/update-center.json", "/platform-plugins.json");
            try {
                URLConnection connection = ProxyConfiguration.open(new URL(suggestedPluginUrl));
                
                try {
                    if(connection instanceof HttpURLConnection) {
                        int responseCode = ((HttpURLConnection)connection).getResponseCode();
                        if(HttpURLConnection.HTTP_OK != responseCode) {
                            throw new HttpRetryException("Invalid response code (" + responseCode + ") from URL: " + suggestedPluginUrl, responseCode);
                        }
                    }
                    
                    String initialPluginJson = IOUtils.toString(connection.getInputStream(), "utf-8");
                    initialPluginList = JSONArray.fromObject(initialPluginJson);
                    break updateSiteList;
                } catch(Exception e) {
                    // not found or otherwise unavailable
                    LOGGER.log(Level.FINE, e.getMessage(), e);
                    continue updateSiteList;
                }
            } catch(Exception e) {
                LOGGER.log(Level.FINE, e.getMessage(), e);
            }
        }
        if (initialPluginList == null) {
            // fall back to local file
            try {
                ClassLoader cl = getClass().getClassLoader();
                URL localPluginData = cl.getResource("jenkins/install/platform-plugins.json");
                String initialPluginJson = IOUtils.toString(localPluginData.openStream(), "utf-8");
                initialPluginList =  JSONArray.fromObject(initialPluginJson);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            }
        }
        return initialPluginList;
    }

    /**
     * Get the platform plugins added in the version range
     */
    /*package*/ JSONArray getPlatformPluginsForUpdate(VersionNumber from, VersionNumber to) {
        Jenkins jenkins = Jenkins.getInstance();
        JSONArray pluginCategories = JSONArray.fromObject(getPlatformPluginList().toString());
        for (Iterator<?> categoryIterator = pluginCategories.iterator(); categoryIterator.hasNext();) {
            Object category = categoryIterator.next();
            if (category instanceof JSONObject) {
                JSONObject cat = (JSONObject)category;
                JSONArray plugins = cat.getJSONArray("plugins");
                
                nextPlugin: for (Iterator<?> pluginIterator = plugins.iterator(); pluginIterator.hasNext();) {
                    Object pluginData = pluginIterator.next();
                    if (pluginData instanceof JSONObject) {
                        JSONObject plugin = (JSONObject)pluginData;
                        if (plugin.has("added")) {
                            String sinceVersion = plugin.getString("added");
                            if (sinceVersion != null) {
                                VersionNumber v = new VersionNumber(sinceVersion);
                                if(v.compareTo(to) <= 0 && v.compareTo(from) > 0) {
                                    // This plugin is valid, we'll leave "suggested" state
                                    // to match the experience during install
                                    // but only add it if it's currently uninstalled
                                    String pluginName = plugin.getString("name");
                                    if (null == jenkins.getPluginManager().getPlugin(pluginName)) {
                                        // Also check that a compatible version exists in an update site
                                        boolean foundCompatibleVersion = false;
                                        for (UpdateSite site : jenkins.getUpdateCenter().getSiteList()) {
                                            UpdateSite.Plugin sitePlug = site.getPlugin(pluginName);
                                            if (sitePlug != null
                                                    && !sitePlug.isForNewerHudson()
                                                    && !sitePlug.isNeededDependenciesForNewerJenkins()) {
                                                foundCompatibleVersion = true;
                                                break;
                                            }
                                        }
                                        if (foundCompatibleVersion) {
                                            continue nextPlugin;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    pluginIterator.remove();
                }
                
                if (plugins.isEmpty()) {
                    categoryIterator.remove();
                }
            }
        }
        return pluginCategories;
    }

    /**
     * Gets the file used to store the initial admin password
     */
    public FilePath getInitialAdminPasswordFile() {
        return Jenkins.getInstance().getRootPath().child("secrets/initialAdminPassword");
    }

    /**
     * Remove the setupWizard filter, ensure all updates are written to disk, etc
     */
    public HttpResponse doCompleteInstall() throws IOException, ServletException {
        completeSetup();
        return HttpResponses.okJSON();
    }
    
    /*package*/ void completeSetup() throws IOException, ServletException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        InstallUtil.saveLastExecVersion();
        setCurrentLevel(Jenkins.getVersion());
        PluginServletFilter.removeFilter(FORCE_SETUP_WIZARD_FILTER);
        isUsingSecurityToken = false; // this should not be considered new anymore
        InstallUtil.proceedToNextStateFrom(InstallState.INITIAL_SETUP_COMPLETED);
    }
    
    /**
     * Gets all the install states
     */
    public List<InstallState> getInstallStates() {
        return InstallState.all();
    }
    
    /**
     * Returns an installState by name
     */
    public InstallState getInstallState(String name) {
        if (name == null) {
            return null;
        }
        return InstallState.valueOf(name);
    }
    
    /**
     * This filter will validate that the security token is provided
     */
    private final Filter FORCE_SETUP_WIZARD_FILTER = new Filter() {
        @Override
        public void init(FilterConfig cfg) throws ServletException {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            // Force root requests to the setup wizard
            if (request instanceof HttpServletRequest) {
                HttpServletRequest req = (HttpServletRequest)request;
                if((req.getContextPath() + "/").equals(req.getRequestURI())) {
                    chain.doFilter(new HttpServletRequestWrapper(req) {
                        public String getRequestURI() {
                            return getContextPath() + "/setupWizard/";
                        }
                    }, response);
                    return;
                }
                // fall through to handling the request normally
            }
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    };
}
