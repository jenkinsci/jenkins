package hudson.security;

import hudson.cli.CLI;
import hudson.cli.CLICommand;
import hudson.cli.ClientAuthenticationCache;
import hudson.cli.LoginCommand;
import hudson.cli.LogoutCommand;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.apache.commons.io.input.NullInputStream;
import org.junit.Assert;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class CliAuthenticationTest extends HudsonTestCase {
    public void test1() throws Exception {
        // dummy security realm that authenticates when username==password
        jenkins.setSecurityRealm(createDummySecurityRealm());

        successfulCommand("test","--username","abc","--password","abc");
    }

    private void successfulCommand(String... args) throws Exception {
        assertEquals(0, command(args));
    }

    private int command(String... args) throws Exception {
        CLI cli = new CLI(getURL());
        try {
            return cli.execute(args);
        } finally {
            cli.close();
        }
    }

    private String commandAndOutput(String... args) throws Exception {
        CLI cli = new CLI(getURL());
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            cli.execute(Arrays.asList(args), new NullInputStream(0), baos, baos);
            return baos.toString();
        } finally {
            cli.close();
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
            Assert.assertNotSame(Jenkins.ANONYMOUS,auth);
            Assert.assertEquals("abc", auth.getName());
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
            Assert.assertSame(Jenkins.ANONYMOUS,auth);
            return 0;
        }
    }

    @For({LoginCommand.class, LogoutCommand.class, ClientAuthenticationCache.class})
    public void testLogin() throws Exception {
        jenkins.setSecurityRealm(createDummySecurityRealm());

        successfulCommand("login","--username","abc","--password","abc");
        successfulCommand("test"); // now we can run without an explicit credential
        successfulCommand("logout");
        successfulCommand("anonymous"); // now we should run as anonymous
    }

    /**
     * Login failure shouldn't reveal information about the existence of user
     */
    public void testSecurity110() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false,false,null);
        jenkins.setSecurityRealm(realm);
        jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        realm.createAccount("alice","alice");

        String out1 = commandAndOutput("help", "--username", "alice", "--password", "bogus");
        String out2 = commandAndOutput("help", "--username", "bob", "--password", "bogus");

        assertTrue(out1.contains("Bad Credentials. Search the server log for"));
        assertTrue(out2.contains("Bad Credentials. Search the server log for"));
    }
}
