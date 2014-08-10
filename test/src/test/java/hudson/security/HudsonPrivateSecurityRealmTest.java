package hudson.security;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.model.User;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.*;
import org.jvnet.hudson.test.recipes.LocalData;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.xml.sax.SAXException;

import java.io.*;

import static hudson.security.HudsonPrivateSecurityRealm.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

/**
 * @author Kohsuke Kawaguchi
 */
@For(HudsonPrivateSecurityRealm.class)
public class HudsonPrivateSecurityRealmTest {

	public File getUserFile(User user){
		return new File(Jenkins.getInstance().getRootDir(), "users/" + User.idStrategy().filenameOf(user.getId()) +"/config.xml");
	}

	@Rule
	public JenkinsRule j = new JenkinsRule();

    /**
     * Tests the data compatibility with Hudson before 1.283.
     * Starting 1.283, passwords are now stored hashed.
     */
    @Bug(2381)
    @LocalData
    public void testDataCompatibilityWith1_282() throws Exception {
        // make sure we can login with the same password as before
        JenkinsRule.WebClient wc = j.createWebClient().login("alice", "alice");

        try {
            // verify the sanity that the password is really used
            // this should fail
			j.createWebClient().login("bob", "bob");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(401,e.getStatusCode());
        }

        // resubmit the config and this should force the data store to be rewritten
        HtmlPage p = wc.goTo("user/alice/configure");
        j.submit(p.getFormByName("config"));

        // verify that we can still login
        j.createWebClient().login("alice", "alice");
    }

    @WithoutJenkins
    public void testHashCompatibility() {
        String old = CLASSIC.encodePassword("hello world", null);
        assertTrue(PASSWORD_ENCODER.isPasswordValid(old,"hello world",null));

        String secure = PASSWORD_ENCODER.encodePassword("hello world", null);
        assertTrue(PASSWORD_ENCODER.isPasswordValid(old,"hello world",null));

        assertTrue(!secure.equals(old));
    }


	@Test
	@Issue("JENKINS-11205")
	public void testCanDisable() throws Exception {
		GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
		j.jenkins.setAuthorizationStrategy(auth);
		j.jenkins.setCrumbIssuer(null);
		HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
		j.jenkins.setSecurityRealm(realm);
		User user = realm.createAccount("John Smith","password");
		String password2 = "password2";
		User user2 = realm.createAccount("John Smith2",password2);
		user2.save();
		auth.add(Jenkins.ADMINISTER, user.getId());
		auth.add(Jenkins.READ, user2.getId());
		auth.add(Jenkins.READ,"anonymous");

		SecurityContextHolder.getContext().setAuthentication(user.impersonate());
		HudsonPrivateSecurityRealm.Details property = user.getProperty(HudsonPrivateSecurityRealm.Details.class);
		HudsonPrivateSecurityRealm.Details property2 = user2.getProperty(HudsonPrivateSecurityRealm.Details.class);

		assertTrue("User should be enabled by default", property.isEnabled());
		assertTrue("User2 should be enabled by default", property2.isEnabled());

		//checking that may login before
		try {
			j.createWebClient().login(user2.getId(), password2);
		} catch (Exception e) {
			fail("We should be able to login user2: " + user2.getId());
		}

		// update from previous configs simulation by removing line from xml config
		File user2File = getUserFile(user2);

		FileReader fileReader = new FileReader(user2File);
		BufferedReader br = new BufferedReader(fileReader);
		File otherUserF = new File(getUserFile(user2).toString() + ".tmp");
		FileWriter fw = new FileWriter(otherUserF);
		BufferedWriter bw = new BufferedWriter(fw);

		String line;
		boolean mark = false;
		while ((line = br.readLine()) != null) {
			if (line.matches("(.*)<hudson.security.HudsonPrivateSecurityRealm_-Details>(.*)"))
				mark = true;
			if (!(line.matches("(.*)<enabled>true</enabled>(.*)")) || !mark){
				bw.write(line);
				bw.newLine();
			}
		}
		br.close();
		bw.close();
		assertTrue("renaming config", otherUserF.renameTo(user2File));

		User.reload();
		JenkinsRule.WebClient login2 = null;
		try {
			login2 = j.createWebClient().login(user2.getId(), password2);
		} catch (Exception e) {
			fail("We should be able to login user2 from old config");
		}
		assertNotNull(login2);
		try {
			login2.goTo("logout");
		} catch (SAXException e) {
			fail("Can't logout user2: "+ user2.getId());
		}

		// login user2 before next test
		try {
			login2 = j.createWebClient().login(user2.getId(), password2);
		} catch (Exception e) {
			fail("We should be able to login user2");
		}
		assertNotNull("We should be able to login user2", login2);

		//test disable user2
		try {
			HtmlForm form = j.createWebClient().login(user.getId(), "password").goTo(user2.getUrl() + "/configure").getFormByName("config");
			form.getInputByName("user.enabled").setChecked(false);
			j.submit(form);
		} catch (Exception e) {
			fail("Can't disable user2 using admin user from webui");
		}
		assertFalse("User should have perm to disable user2", user2.getProperty(HudsonPrivateSecurityRealm.Details.class).isEnabled());

		//prevent self unlock
		HtmlForm form = login2.goTo(user2.getUrl() + "/configure").getFormByName("config");
		j.submit(form);
		assertFalse("User2 should be disabled! Self unlocking detected", user2.getProperty(HudsonPrivateSecurityRealm.Details.class).isEnabled());

		// now fail on login user2
		boolean fail = false;
		try {
			//creating new session
			login2 = j.createWebClient().login(user2.getId(), password2);
		} catch (Exception e) {
			fail = true;
		}
		assertTrue("user2 must fail on new login after it was disabled", fail);
	}


}
