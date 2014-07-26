package hudson.cli;

import hudson.ExtensionComponent;
import hudson.cli.declarative.CLIRegisterer;
import hudson.model.Hudson;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

public class CLIRegistererTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testAuthWithSecurityRealmCLIAuthenticator() {
        j.getInstance().setSecurityRealm(j.createDummySecurityRealm());

        Collection<ExtensionComponent<CLICommand>> extensions = new CLIRegisterer().find(CLICommand.class, (Hudson) j.getInstance());

        CLICommand quietCommand = null;
        CLICommand cancelCommand = null;

        Assert.assertFalse("Jenkins not in quiet down mode", Jenkins.getInstance().isQuietingDown());

        for (ExtensionComponent<CLICommand> extension : extensions) {
            CLICommand command = extension.getInstance();
            if (command.getName().equals("quiet-down")) {
                quietCommand = command;
            }
            if (command.getName().equals("cancel-quiet-down")) {
                cancelCommand = command;
            }
        }

        Assert.assertNotNull(quietCommand);
        Assert.assertNotNull(cancelCommand);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);

        // run the CLI command with valid credentials
        int quietResult = quietCommand.main(Arrays.asList("--username", "foo", "--password", "foo"), Locale.ENGLISH, bais, out, out);
        Assert.assertTrue("Jenkins put into quiet down mode", Jenkins.getInstance().isQuietingDown());

        // run the CLI command with invalid credentials
        int cancelResult = cancelCommand.main(Arrays.asList("--username", "foo", "--password", "invalid"), Locale.ENGLISH, bais, out, out);
        Assert.assertTrue("Jenkins still in quiet down mode", Jenkins.getInstance().isQuietingDown());
    }
}
