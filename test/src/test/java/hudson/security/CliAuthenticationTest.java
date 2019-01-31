package hudson.security;

import hudson.ExtensionList;
import hudson.ExtensionList;
import hudson.cli.CLI;
import hudson.cli.CLICommand;
import hudson.cli.CliProtocol2;
import jenkins.model.Jenkins;
import jenkins.security.SecurityListener;
import jenkins.security.SpySecurityListener;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.userdetails.User;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.commons.io.input.NullInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.springframework.dao.DataAccessException;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("deprecation") // Remoting-based CLI usages intentional
public class CliAuthenticationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    private SpySecurityListener spySecurityListener;

    @Before
    public void prepareListeners(){
        //TODO simplify using #3021 into ExtensionList.lookupSingleton(SpySecurityListener.class)
        this.spySecurityListener = ExtensionList.lookup(SecurityListener.class).get(SpySecurityListenerImpl.class);
    }

    @Before
    public void setUp() {
        Set<String> agentProtocols = new HashSet<>(j.jenkins.getAgentProtocols());
        agentProtocols.add(ExtensionList.lookupSingleton(CliProtocol2.class).getName());
       j.jenkins.setAgentProtocols(agentProtocols);
    }

    @Test
    public void test() throws Exception {
        // dummy security realm that authenticates when username==password
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        successfulCommand("test","--username","abc","--password","abc");
    }

    private void successfulCommand(String... args) throws Exception {
        assertEquals(0, command(args));
    }

    private void unsuccessfulCommand(String... args) throws Exception {
        assertNotEquals(0, command(args));
    }

    private int command(String... args) throws Exception {
        try (CLI cli = new CLI(j.getURL())) {
            return cli.execute(args);
        }
    }

    private String commandAndOutput(String... args) throws Exception {
        try (CLI cli = new CLI(j.getURL())) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            cli.execute(Arrays.asList(args), new NullInputStream(0), baos, baos);
            return baos.toString();
        }
    }

    @TestExtension
    public static class TestCommand extends CLICommand {
        @Override
        public String getShortDescription() {
            return "test command";
        }

        @Override
        protected int run() throws Exception {
            Authentication auth = Jenkins.getAuthentication();
            assertNotSame(Jenkins.ANONYMOUS,auth);
            assertEquals("abc", auth.getName());
            return 0;
        }
    }

    @TestExtension
    public static class AnonymousCommand extends CLICommand {
        @Override
        public String getShortDescription() {
            return "makes sure that the command is running as anonymous user";
        }

        @Override
        protected int run() throws Exception {
            Authentication auth = Jenkins.getAuthentication();
            assertSame(Jenkins.ANONYMOUS,auth);
            return 0;
        }
    }

    @Test
    @For({hudson.cli.LoginCommand.class, hudson.cli.LogoutCommand.class, hudson.cli.ClientAuthenticationCache.class})
    public void login() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        successfulCommand("login","--username","abc","--password","abc");
        spySecurityListener.authenticatedCalls.assertLastEventIsAndThenRemoveIt(userDetails -> userDetails.getUsername().equals("abc"));
        spySecurityListener.loggedInCalls.assertLastEventIsAndThenRemoveIt("abc");

        successfulCommand("test"); // now we can run without an explicit credential
        spySecurityListener.authenticatedCalls.assertLastEventIsAndThenRemoveIt(userDetails -> userDetails.getUsername().equals("abc"));
        spySecurityListener.loggedInCalls.assertNoNewEvents();

        successfulCommand("logout");
        spySecurityListener.authenticatedCalls.assertLastEventIsAndThenRemoveIt(userDetails -> userDetails.getUsername().equals("abc"));
        spySecurityListener.loggedOutCalls.assertLastEventIsAndThenRemoveIt("abc");

        successfulCommand("anonymous"); // now we should run as anonymous
        spySecurityListener.authenticatedCalls.assertNoNewEvents();
        spySecurityListener.failedToAuthenticateCalls.assertNoNewEvents();
        spySecurityListener.loggedInCalls.assertNoNewEvents();
        spySecurityListener.loggedOutCalls.assertNoNewEvents();
    }

    @Test
    @Issue("JENKINS-27026")
    public void loginAsALegitimateUserButUnknown() throws Exception {
        j.jenkins.setSecurityRealm(new MockSecurityRealm());

        String username = "alice";

        unsuccessfulCommand("login","--username",username,"--password","badCredentials");
        spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt(username);
        spySecurityListener.failedToLogInCalls.assertNoNewEvents();
        spySecurityListener.authenticatedCalls.assertNoNewEvents();
        spySecurityListener.loggedInCalls.assertNoNewEvents();
//        spySecurityListener.failedToLogInCalls.assertLastEventIsAndThenRemoveIt(username);

        unsuccessfulCommand("login","--username",username,"--password","usernameNotFound");
        spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt(username);
        spySecurityListener.failedToLogInCalls.assertNoNewEvents();
        spySecurityListener.authenticatedCalls.assertNoNewEvents();
        spySecurityListener.loggedInCalls.assertNoNewEvents();
//        spySecurityListener.failedToLogInCalls.assertLastEventIsAndThenRemoveIt(username);

        unsuccessfulCommand("login","--username",username,"--password","mayOrMayNotExist");
        spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt(username);
        spySecurityListener.failedToLogInCalls.assertNoNewEvents();
        spySecurityListener.authenticatedCalls.assertNoNewEvents();
        spySecurityListener.loggedInCalls.assertNoNewEvents();
//        spySecurityListener.failedToLogInCalls.assertLastEventIsAndThenRemoveIt(username);

        // in case of authentication that throw a DataAccessException (see impersonatingUserDetailsService)
        // the CLI login command does not work as expected
        unsuccessfulCommand("login","--username",username,"--password","dataAccess");
        spySecurityListener.failedToAuthenticateCalls.assertNoNewEvents();
        spySecurityListener.failedToLogInCalls.assertNoNewEvents();
        spySecurityListener.authenticatedCalls.assertNoNewEvents();
        spySecurityListener.loggedInCalls.assertNoNewEvents();
//        spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt(username);
//        spySecurityListener.failedToLogInCalls.assertLastEventIsAndThenRemoveIt(username);
    }

    private static class MockSecurityRealm extends AbstractPasswordBasedSecurityRealm {
        @Override protected UserDetails authenticate(String username, String password) throws AuthenticationException {
            switch(password){
                case "badCredentials": throw new BadCredentialsException("BadCredentials requested");
                case "usernameNotFound": throw new UsernameNotFoundException("UsernameNotFound requested");
                case "mayOrMayNotExist": throw new UserMayOrMayNotExistException("MayOrMayNotExist requested");
                case "dataAccess": throw new DataAccessException("DataAccess requested"){};
                default:
                    return new User(
                        username, password,
                        true, true, true,
                        new GrantedAuthority[]{SecurityRealm.AUTHENTICATED_AUTHORITY}
                    );
            }
        }

        @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            return null;
        }

        @Override public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
            return null;
        }
    }

    /**
     * Login failure shouldn't reveal information about the existence of user
     */
    @Test
    public void security110() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false,false,null);
        j.jenkins.setSecurityRealm(realm);
        j.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        realm.createAccount("alice","alice");

        String out1 = commandAndOutput("help", "--username", "alice", "--password", "bogus");
        String out2 = commandAndOutput("help", "--username", "bob", "--password", "bogus");

        assertTrue(out1.contains("Bad Credentials. Search the server log for"));
        assertTrue(out2.contains("Bad Credentials. Search the server log for"));
    }

    @TestExtension
    public static class SpySecurityListenerImpl extends SpySecurityListener {}
}
