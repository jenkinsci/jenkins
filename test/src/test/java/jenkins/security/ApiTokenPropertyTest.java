package jenkins.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.Util;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;

import java.net.HttpURLConnection;
import java.net.URL;

import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.security.apitoken.ApiTokenStore;
import jenkins.security.apitoken.ApiTokenTestHelper;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Kohsuke Kawaguchi
 */
public class ApiTokenPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setupLegacyConfig(){
        ApiTokenTestHelper.enableLegacyBehavior();
    }
    
    /**
     * Tests the UI interaction and authentication.
     */
    @Test
    public void basics() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User u = User.getById("foo", true);
        final ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();

        // Make sure that user is able to get the token via the interface
        try (ACLContext acl = ACL.as(u)) {
            assertEquals("User is unable to get its own token", token, t.getApiToken());
        }

        // test the authentication via Token
        WebClient wc = createClientForUser("foo");
        assertEquals(u, wc.executeOnServer(new Callable<User>() {
            public User call() throws Exception {
                return User.current();
            }
        }));
        
        // Make sure the UI shows the token to the user
        HtmlPage config = wc.goTo(u.getUrl() + "/configure");
        HtmlForm form = config.getFormByName("config");
        assertEquals(token, form.getInputByName("_.apiToken").getValueAttribute());

        // round-trip shouldn't change the API token
        j.submit(form);
        assertSame(t, u.getProperty(ApiTokenProperty.class));
    }

    @Test
    public void security49Upgrade() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User u = User.get("foo");
        String historicalInitialValue = Util.getDigestOf(Jenkins.get().getSecretKey() + ":" + u.getId());

        // we won't accept historically used initial value as it may be compromised
        ApiTokenProperty t = new ApiTokenProperty(historicalInitialValue);
        u.addProperty(t);
        String apiToken1 = t.getApiToken();
        assertNotEquals(apiToken1, Util.getDigestOf(historicalInitialValue));

        // the replacement for the compromised value must be consistent and cannot be random
        ApiTokenProperty t2 = new ApiTokenProperty(historicalInitialValue);
        u.addProperty(t2);
        assertEquals(apiToken1,t2.getApiToken());

        // any other value is OK. those are changed values
        t = new ApiTokenProperty(historicalInitialValue+"somethingElse");
        u.addProperty(t);
        assertEquals(t.getApiToken(), Util.getDigestOf(historicalInitialValue+"somethingElse"));
    }
    
    @Issue("SECURITY-200")
    @Test
    public void adminsShouldBeUnableToSeeTokensByDefault() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User u = User.get("foo");
        final ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();
        
        // Make sure the UI does not show the token to another user
        WebClient wc = createClientForUser("bar");
        HtmlPage config = wc.goTo(u.getUrl() + "/configure");
        HtmlForm form = config.getFormByName("config");
        assertEquals(Messages.ApiTokenProperty_ChangeToken_TokenIsHidden(), form.getInputByName("_.apiToken").getValueAttribute());
    }
    
    @Issue("SECURITY-200")
    @Test
    public void adminsShouldBeUnableToChangeTokensByDefault() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.get("foo");
        User bar = User.get("bar");
        final ApiTokenProperty t = foo.getProperty(ApiTokenProperty.class);
        final ApiTokenProperty.DescriptorImpl descriptor = (ApiTokenProperty.DescriptorImpl) t.getDescriptor();
        
        // Make sure that Admin can reset a token of another user
        WebClient wc = createClientForUser("bar")
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage requirePOST = wc.goTo(foo.getUrl() + "/" + descriptor.getDescriptorUrl()+ "/changeToken");
        assertEquals("method should not be allowed", 
                HttpURLConnection.HTTP_BAD_METHOD, 
                requirePOST.getWebResponse().getStatusCode());

        wc.setThrowExceptionOnFailingStatusCode(true);
        WebRequest request = new WebRequest(new URL(j.getURL().toString() + foo.getUrl() + "/" + descriptor.getDescriptorUrl()+ "/changeToken"), HttpMethod.POST);
        HtmlPage res = wc.getPage(request);

        // TODO This nicer alternative requires https://github.com/jenkinsci/jenkins/pull/2268 or similar to work
//        HtmlPage res = requirePOST.getPage().getForms().get(0).getElementsByAttribute("input", "type", "submit").get(0).click();
        assertEquals("Update token response is incorrect", 
                Messages.ApiTokenProperty_ChangeToken_SuccessHidden(), "<div>" + res.getBody().asText() + "</div>");
    }

    @Test
    public void postWithUsernameAndTokenInBasicAuthHeader() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("bar");
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User.getById("foo", true);

        WebClient wc = createClientForUser("foo");
        WebRequest wr = new WebRequest(new URL(j.getURL(), "job/bar/build"), HttpMethod.POST);

        assertEquals(HttpURLConnection.HTTP_CREATED, wc.getPage(wr).getWebResponse().getStatusCode());

        j.waitUntilNoActivity();

        Cause.UserIdCause triggeredBy = p.getBuildByNumber(1).getCause(Cause.UserIdCause.class);
        assertEquals("foo", triggeredBy.getUserId());
    }

    @NonNull
    private WebClient createClientForUser(final String id) throws Exception {
        User u = User.getById(id, true);
        
        WebClient wc = j.createWebClient();
        wc.withBasicApiToken(u);
        return wc;
    }
    
    @Test
    @Issue("JENKINS-32776")
    public void generateNewTokenWithoutName() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    
        // user is still able to connect with legacy token
        User admin = User.getById("admin", true);
    
        WebClient wc = j.createWebClient();
        wc.withBasicCredentials("admin", "admin");
        
        GenerateNewTokenResponse token1 = generateNewToken(wc, "admin", "");
        assertNotEquals("", token1.tokenName.trim());
        
        GenerateNewTokenResponse token2 = generateNewToken(wc, "admin", "New Token");
        assertEquals("New Token", token2.tokenName);
    }
    
    @Test
    @LocalData
    @Issue("JENKINS-32776")
    public void migrationFromLegacyToken() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        
        // user is still able to connect with legacy token
        User admin = User.getById("admin", false);
        assertNotNull("Admin user not configured correctly in local data", admin);
        ApiTokenProperty apiTokenProperty = admin.getProperty(ApiTokenProperty.class);
        
        WebClient wc = j.createWebClient();
        wc.withBasicCredentials("admin", "admin");
        checkUserIsConnected(wc);
        
        // 7be8e81ad5a350fa3f3e2acfae4adb14
        String localLegacyToken = apiTokenProperty.getApiTokenInsecure();
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", localLegacyToken);
        checkUserIsConnected(wc);
        
        // can still renew it after (using API)
        assertEquals(1, apiTokenProperty.getTokenList().size());
        apiTokenProperty.changeApiToken();
        assertEquals(1, apiTokenProperty.getTokenList().size());
        String newLegacyToken = apiTokenProperty.getApiTokenInsecure();
        
        // use the new legacy api token
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", newLegacyToken);
        checkUserIsConnected(wc);
        
        // but previous one is not more usable
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", localLegacyToken);
        checkUserIsNotConnected(wc);
        
        // ===== new system =====
        
        // revoke the legacy
        ApiTokenStore.HashedToken legacyToken = apiTokenProperty.getTokenStore().getLegacyToken();
        assertNotNull(legacyToken);
        String legacyUuid = legacyToken.getUuid();
    
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", newLegacyToken);
        revokeToken(wc, "admin", legacyUuid);
        
        assertEquals(0, apiTokenProperty.getTokenList().size());
        
        // check it does not work any more
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", newLegacyToken);
        checkUserIsNotConnected(wc);
    
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", localLegacyToken);
        checkUserIsNotConnected(wc);
    
        // ensure the user can still connect using its username / password
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", "admin");
        checkUserIsConnected(wc);
        
        // generate new token with the new system
        wc = j.createWebClient();
        wc.login("admin", "admin");
        GenerateNewTokenResponse newToken = generateNewToken(wc, "admin", "New Token");
        
        // use the new one
        wc = j.createWebClient();
        wc.withBasicCredentials("admin", newToken.tokenValue);
        checkUserIsConnected(wc);
    }
    
    private void checkUserIsConnected(WebClient wc) throws Exception {
        XmlPage xmlPage = wc.goToXml("whoAmI/api/xml");
        assertThat(xmlPage, hasXPath("//name", is("admin")));
        assertThat(xmlPage, hasXPath("//anonymous", is("false")));
        assertThat(xmlPage, hasXPath("//authenticated", is("true")));
        assertThat(xmlPage, hasXPath("//authority", is("authenticated")));
    }
    
    private void checkUserIsNotConnected(WebClient wc) throws Exception {
        try{
            wc.goToXml("whoAmI/api/xml");
            fail();
        }
        catch(FailingHttpStatusCodeException e){
            assertEquals(401, e.getStatusCode());
        }
    }
    
    @Test
    @Issue("JENKINS-32776")
    public void legacyTokenChange() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        
        config.setTokenGenerationOnCreationEnabled(true);
        
        User user = User.getById("user", true);
        WebClient wc = j.createWebClient();
        wc.withBasicCredentials("user", "user");
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        
        { // with one legacy token, we can change it using web UI or direct internal call
            String currentLegacyToken = apiTokenProperty.getApiToken();
            assertEquals(1, apiTokenProperty.getTokenList().size());
            
            config.setCreationOfLegacyTokenEnabled(true);
            {
                // change using web UI
                changeLegacyToken(wc, "user", true);
                String newLegacyToken = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken, currentLegacyToken);
                
                // change using internal call
                apiTokenProperty.changeApiToken();
                String newLegacyToken2 = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken2, newLegacyToken);
                assertNotEquals(newLegacyToken2, currentLegacyToken);
                
                currentLegacyToken = newLegacyToken2;
            }
            
            config.setCreationOfLegacyTokenEnabled(false);
            {
                // change using web UI
                changeLegacyToken(wc, "user", true);
                String newLegacyToken = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken, currentLegacyToken);
                
                // change using internal call
                apiTokenProperty.changeApiToken();
                String newLegacyToken2 = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken2, newLegacyToken);
                assertNotEquals(newLegacyToken2, currentLegacyToken);
            }
        }
        { // but without any legacy token, the direct internal call remains but web UI depends on config
            revokeAllToken(wc, user);
    
            checkCombinationWithConfigAndMethodForLegacyTokenCreation(config, wc, user);
        }
        {// only the legacy token have impact on that capability
            generateNewToken(wc, "user", "New token");
    
            checkCombinationWithConfigAndMethodForLegacyTokenCreation(config, wc, user);
        }
    }
    
    private void checkCombinationWithConfigAndMethodForLegacyTokenCreation(
            ApiTokenPropertyConfiguration config, WebClient wc, User user
    ) throws Exception {
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        
        config.setCreationOfLegacyTokenEnabled(true);
        {
            {// change using web UI
                changeLegacyToken(wc, "user", true);
                String newLegacyToken = apiTokenProperty.getApiToken();
                assertNotEquals(newLegacyToken, Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed());
            }
            revokeLegacyToken(wc, user);
        
            // always possible
            changeTokenByDirectCall(apiTokenProperty);
            revokeLegacyToken(wc, user);
        }
    
        revokeAllToken(wc, user);
    
        config.setCreationOfLegacyTokenEnabled(false);
        {
            {// change not possible using web UI
                changeLegacyToken(wc, "user", false);
                String newLegacyToken = apiTokenProperty.getApiToken();
                assertEquals(newLegacyToken, Messages.ApiTokenProperty_NoLegacyToken());
            }
            revokeLegacyToken(wc, user);
        
            // always possible
            changeTokenByDirectCall(apiTokenProperty);
            revokeLegacyToken(wc, user);
        }
    }
    
    private void changeTokenByDirectCall(ApiTokenProperty apiTokenProperty) throws Exception {
        apiTokenProperty.changeApiToken();
        String newLegacyToken = apiTokenProperty.getApiToken();
        assertNotEquals(newLegacyToken, Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed());
    }
    
    private void revokeAllToken(WebClient wc, User user) throws Exception {
        revokeAllTokenUsingFilter(wc, user, it -> true);
    }
    
    private void revokeLegacyToken(WebClient wc, User user) throws Exception {
        revokeAllTokenUsingFilter(wc, user, ApiTokenStore.HashedToken::isLegacy);
    }
    
    private void revokeAllTokenUsingFilter(WebClient wc, User user, Predicate<ApiTokenStore.HashedToken> filter) throws Exception {
        ApiTokenProperty apiTokenProperty = user.getProperty(ApiTokenProperty.class);
        List<String> uuidList = apiTokenProperty.getTokenStore().getTokenListSortedByName().stream()
                .filter(filter)
                .map(ApiTokenStore.HashedToken::getUuid)
                .collect(Collectors.toList());
        for(String uuid : uuidList){
            revokeToken(wc, user.getId(), uuid);
        }
    }
    
    private void revokeToken(WebClient wc, String login, String tokenUuid) throws Exception {
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + login + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/revoke/?tokenUuid=" + tokenUuid),
                HttpMethod.POST
        );
        Page p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());
    }
    
    private void changeLegacyToken(WebClient wc, String login, boolean success) throws Exception {
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + login + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/changeToken/"),
                HttpMethod.POST
        );
        Page p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());
        if(success){
            assertThat(p.getWebResponse().getContentAsString(), not(containsString(Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed())));
        }else{
            assertThat(p.getWebResponse().getContentAsString(), containsString(Messages.ApiTokenProperty_ChangeToken_CapabilityNotAllowed()));
        }
    }
    
    public static class GenerateNewTokenResponse {
        public String tokenUuid;
        public String tokenName;
        public String tokenValue;
    }
    
    private GenerateNewTokenResponse generateNewToken(WebClient wc, String login, String tokenName) throws Exception {
        WebRequest request = new WebRequest(
                new URL(j.getURL(), "user/" + login + "/descriptorByName/" + ApiTokenProperty.class.getName() + "/generateNewToken/?newTokenName=" + tokenName),
                HttpMethod.POST
        );
        Page p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());
        
        String response = p.getWebResponse().getContentAsString();
        JSONObject responseJson = JSONObject.fromObject(response);
        Object result = responseJson.getJSONObject("data").toBean(GenerateNewTokenResponse.class);
        return (GenerateNewTokenResponse) result;
    }
    
    
    // test no token are generated for new user with the global configuration set to false
}
