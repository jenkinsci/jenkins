package jenkins.install;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import hudson.FilePath;
import hudson.ProxyConfiguration;
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
import jenkins.model.Jenkins;
import jenkins.security.s2m.AdminWhitelistRule;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import static org.apache.commons.io.FileUtils.readFileToString;
import org.apache.commons.io.IOUtils;
import static org.apache.commons.lang.StringUtils.defaultIfBlank;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * A Jenkins instance used during first-run to provide a limited set of services while
 * initial installation is in progress
 * 
 * @since 2.0
 */
@Restricted(NoExternalUse.class)
public class SetupWizard {
    /**
     * The security token parameter name
     */
    public static String initialSetupAdminUserName = "admin";

    private final static Logger LOGGER = Logger.getLogger(SetupWizard.class.getName());

    private final Jenkins jenkins;

    public SetupWizard(Jenkins j) throws IOException, InterruptedException {
        this.jenkins = j;

        UpgradeWizard.completeUpgrade(j);
        
        // Create an admin user by default with a 
        // difficult password
        FilePath iapf = getInitialAdminPasswordFile();
        if(j.getSecurityRealm() == null || j.getSecurityRealm() == SecurityRealm.NO_AUTHENTICATION) { // this seems very fragile
            BulkChange bc = new BulkChange(j);
            try{
                HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
                j.setSecurityRealm(securityRealm);
                String randomUUID = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ENGLISH);

                // create an admin user
                securityRealm.createAccount(SetupWizard.initialSetupAdminUserName, randomUUID);

                // JENKINS-33599 - write to a file in the jenkins home directory
                // most native packages of Jenkins creates a machine user account 'jenkins' to run Jenkins,
                // and use group 'jenkins' for admins. So we allo groups to read this file
                iapf.touch(System.currentTimeMillis());
                iapf.chmod(0640);
                iapf.write(randomUUID + System.lineSeparator(), "UTF-8");
                

                // Lock Jenkins down:
                FullControlOnceLoggedInAuthorizationStrategy authStrategy = new FullControlOnceLoggedInAuthorizationStrategy();
                authStrategy.setAllowAnonymousRead(false);
                j.setAuthorizationStrategy(authStrategy);

                // Shut down all the ports we can by default:
                j.setSlaveAgentPort(-1); // -1 to disable

                // require a crumb issuer
                j.setCrumbIssuer(new DefaultCrumbIssuer(false));

                // set master -> slave security:
                j.getInjector().getInstance(AdminWhitelistRule.class)
                    .setMasterKillSwitch(false);
            
                j.save(); // !!
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
        } catch (ServletException e) {
            throw new AssertionError(e);
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
    public boolean useGeneratedPassword() {
        try {
            return getInitialAdminPasswordFile().exists();
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
                
                j.setInstallState(InstallState.CREATE_ADMIN_USER.getNextState());
                
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

    /*package*/ static void completeUpgrade(Jenkins jenkins) throws IOException {
        setCurrentLevel(Jenkins.getVersion());
        // jenkins.getInstallState().proceedToNextState();
    }
    
    /*package*/ static void setCurrentLevel(VersionNumber v) throws IOException {
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
     * What is the version the upgrade wizard has run the last time and upgraded to?
     */
    public static VersionNumber getCurrentLevel() throws IOException {
        VersionNumber from = new VersionNumber("1.0");
        File state = getUpdateStateFile();
        if (state.exists()) {
            from = new VersionNumber(defaultIfBlank(readFileToString(state), "1.0").trim());
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
                    return hudson.util.HttpResponses.okJSON(initialPluginData);
                }
            } else {
                JSONArray initialPluginData = getPlatformPluginList();
                if(initialPluginData != null) {
                    return hudson.util.HttpResponses.okJSON(initialPluginData);
                }
            }
        }
        return hudson.util.HttpResponses.okJSON();
    }
    
    /**
     * Provides the list of platform plugin updates from the last time
     * the upgrade was run
     */
    /*package*/ JSONArray getPlatformPluginUpdates() {
        try {
            return getPlatformPluginsForUpdate(getCurrentLevel(), Jenkins.getVersion());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Gets the suggested plugin list from the update sites, falling back to a local version
     */
    @CheckForNull
    public JSONArray getPlatformPluginList() {
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
                                    plugin.put("suggested", false);
                                    continue nextPlugin;
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
        return jenkins.getRootPath().child("secrets/initialAdminPassword");
    }

    /**
     * Remove the setupWizard filter, ensure all updates are written to disk, etc
     */
    public HttpResponse doCompleteInstall() throws IOException, ServletException {
        completeSetup(jenkins);
        PluginServletFilter.removeFilter(FORCE_SETUP_WIZARD_FILTER);
        return HttpResponses.okJSON();
    }
    
    static void completeSetup(Jenkins jenkins) {
        jenkins.setInstallState(InstallState.INITIAL_SETUP_COMPLETED);
        InstallUtil.saveLastExecVersion();
        try {
            completeUpgrade(jenkins);
        } catch(IOException ex) {
            LOGGER.log(Level.SEVERE, "Cannot update the upgrade status", ex);
        }
        // Also, clean up the setup wizard if it's completed
        jenkins.setSetupWizard(null);
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
