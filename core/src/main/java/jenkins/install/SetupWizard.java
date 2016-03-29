package jenkins.install;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.BulkChange;
import hudson.FilePath;
import hudson.model.Descriptor.FormException;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.GlobalSecurityConfiguration;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.SecurityRealm;
import hudson.security.csrf.DefaultCrumbIssuer;
import hudson.util.HttpResponses;
import hudson.util.PluginServletFilter;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import jenkins.security.s2m.AdminWhitelistRule;

/**
 * A Jenkins instance used during first-run to provide a limited set of services while
 * initial installation is in progress
 * 
 * @since 2.0
 */
public class SetupWizard {
    /**
     * The security token parameter name
     */
    public static String initialSetupAdminUserName = "admin";

    private final Logger LOGGER = Logger.getLogger(SetupWizard.class.getName());

    private final Jenkins jenkins;

    public SetupWizard(Jenkins j) throws IOException, InterruptedException {
        this.jenkins = j;
        
        // don't run the upgrade wizard for new installs
        if(InstallState.NEW.equals(j.getInstallState())) {
            UpgradeWizard uw = jenkins.getInjector().getInstance(UpgradeWizard.class);
            if (uw!=null)
                uw.setCurrentLevel(new VersionNumber("2.0"));
        }
        
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
                iapf.write(randomUUID, "UTF-8");
                iapf.chmod(0640);

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
        
        try {
            PluginServletFilter.addFilter(FORCE_SETUP_WIZARD_FILTER);
        } catch (ServletException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Gets the file used to store the initial admin password
     */
    @Restricted(NoExternalUse.class) // use by Jelly
    public FilePath getInitialAdminPasswordFile() {
        return jenkins.getRootPath().child("secrets/initialAdminPassword");
    }

    /**
     * Remove the setupWizard filter, ensure all updates are written to disk, etc
     */
    public HttpResponse doCompleteInstall() throws IOException, ServletException {
        jenkins.setInstallState(InstallState.INITIAL_SETUP_COMPLETED);
        InstallUtil.saveLastExecVersion();
        PluginServletFilter.removeFilter(FORCE_SETUP_WIZARD_FILTER);
        // Also, clean up the setup wizard if it's completed
        jenkins.setSetupWizard(null);

        return HttpResponses.okJSON();
    }
    
    /**
     * Handle security configuration
     */
    public HttpResponse doSaveSecurityConfig(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        jenkins.checkPermission(Jenkins.ADMINISTER); // this is redundant, maybe
        GlobalSecurityConfiguration securityConfig = (GlobalSecurityConfiguration)jenkins.getDynamic("configureSecurity");
        boolean configureSuccess = securityConfig.configure(req, req.getSubmittedForm());
        if(configureSuccess) {
            jenkins.save();
            jenkins.setInstallState(InstallState.CONFIGURE_SECURITY.getNextState());
            return HttpResponses.okJSON();
        }
        return HttpResponses.errorJSON("Unable to configure security");
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
            // As an extra measure of security, the install wizard generates a security token, and
            // requires the user to enter it before proceeding through the installation. Once set
            // we'll set a cookie so the subsequent operations succeed
            if (request instanceof HttpServletRequest) {
                HttpServletRequest req = (HttpServletRequest)request;
                //if (!Pattern.compile(".*[.](css|ttf|gif|woff|eot|png|js)").matcher(req.getRequestURI()).matches()) {
                    // Allow js & css requests through
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
