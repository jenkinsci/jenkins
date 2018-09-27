package hudson.model;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.model.Cause.UserIdCause;
import hudson.slaves.NodeProvisionerRule;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.net.URL;
import java.util.function.Function;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

//TODO merge into QueueTest after security patch
public class QueueSEC891Test {
    
    @Rule
    public JenkinsRule r = new NodeProvisionerRule(-1, 0, 10);
    
    @Test public void doCancelItem_PermissionIsChecked() throws Exception {
        checkCancelOperationUsingUrl(item -> "queue/cancelItem?id=" + item.getId());
    }
    
    @Test public void doCancelQueue_PermissionIsChecked() throws Exception {
        checkCancelOperationUsingUrl(item -> "queue/item/" + item.getId() + "/cancelQueue");
    }
    
    private void checkCancelOperationUsingUrl(Function<Queue.Item, String> urlProvider) throws Exception {
        Queue q = r.jenkins.getQueue();
        
        r.jenkins.setCrumbIssuer(null);
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.CANCEL).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().to("user")
        );
        
        // prevent execution to push stuff into the queue
        r.jenkins.setNumExecutors(0);
        assertThat(q.getItems().length, equalTo(0));
        
        FreeStyleProject testProject = r.createFreeStyleProject("test");
        testProject.scheduleBuild(new UserIdCause());
        
        Queue.Item[] items = q.getItems();
        assertThat(items.length, equalTo(1));
        Queue.Item currentOne = items[0];
        assertFalse(currentOne.getFuture().isCancelled());
        
        WebRequest request = new WebRequest(new URL(r.getURL() + urlProvider.apply(currentOne)), HttpMethod.POST);
        
        { // user without right cannot cancel
            JenkinsRule.WebClient wc = r.createWebClient();
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
            wc.getOptions().setRedirectEnabled(false);
            wc.login("user");
            Page p = wc.getPage(request);
            // currently the endpoint return a redirection to the previously visited page, none in our case 
            // (so force no redirect to avoid false positive error)
            assertThat(p.getWebResponse().getStatusCode(), lessThan(400));
            
            assertFalse(currentOne.getFuture().isCancelled());
        }
        { // user with right can
            JenkinsRule.WebClient wc = r.createWebClient();
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
            wc.getOptions().setRedirectEnabled(false);
            wc.login("admin");
            Page p = wc.getPage(request);
            assertThat(p.getWebResponse().getStatusCode(), lessThan(400));
            
            assertTrue(currentOne.getFuture().isCancelled());
        }
    }
}
