package hudson.model;

import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.FilePath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

//TODO after the security fix, it could be merged inside UserRestartTest
public class UserRestartSEC897Test {
    
    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();
    
    @Test public void legacyConfigMoveCannotEscapeUserFolder() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                rr.j.jenkins.setSecurityRealm(rr.j.createDummySecurityRealm());
                assertThat(rr.j.jenkins.isUseSecurity(), equalTo(true));
                
                // in order to create the folder "users"
                User.getById("admin", true).save();
                
                { // attempt with ".."
                    JenkinsRule.WebClient wc = rr.j.createWebClient();
                    wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
                    
                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("..", "any-password"));
                    wc.getPage(request);
                }
                { // attempt with "../users/.."
                    JenkinsRule.WebClient wc = rr.j.createWebClient();
                    wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
                    
                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("../users/..", "any-password"));
                    wc.getPage(request);
                }
                
                // security is still active
                assertThat(rr.j.jenkins.isUseSecurity(), equalTo(true));
                // but, the config file was moved
                FilePath rootPath = rr.j.jenkins.getRootPath();
                assertThat(rootPath.child("config.xml").exists(), equalTo(true));
            }
        });
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assertThat(rr.j.jenkins.isUseSecurity(), equalTo(true));
                FilePath rootPath = rr.j.jenkins.getRootPath();
                assertThat(rootPath.child("config.xml").exists(), equalTo(true));
            }
        });
    }
    
    private String base64(String login, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
