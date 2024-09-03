package jenkins.security.stapler;

import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.WebMethod;

@Issue("SECURITY-400")
@For({StaplerDispatchable.class, StaplerNotDispatchable.class, DoActionFilter.class})
public class StaplerRoutableActionTest extends StaplerAbstractTest {

    @TestExtension
    public static class TestNewRulesRoutableAction extends AbstractUnprotectedRootAction {
        // StaplerDispatchable is not enough, the method needs to have at least either a name starting with do* or a WebMethod annotation
        @StaplerDispatchable
        public void notDoName() { replyOk(); }

        @StaplerDispatchable // could be used to indicate that's a web method, without having to use @WebMethod
        public void doWebMethod1() { replyOk(); }

        // without annotation, returnType, parameter, exception => not a web method
        public void doWebMethod2() { replyOk(); }

        public void doWebMethod3() throws HttpResponses.HttpResponseException {
            replyOk();
        }

        public void doWebMethod4(StaplerRequest2 request) {
            replyOk();
        }

        public void doWebMethod5(@QueryParameter String foo) {
            replyOk();
        }
    }

    @Test
    public void testNewRulesRoutableAction_notDoName() throws Exception {
        assertNotReachable("testNewRulesRoutableAction/notDoName/");
        // not even considered as a blocked action because the filter is not even called, they are lacking do* or @WebMethod
        // assertDoActionRequestWasBlockedAndResetFlag();
        assertNotReachable("testNewRulesRoutableAction/tDoName/");
        // assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testNewRulesRoutableAction_webMethod1() throws Exception {
        assertReachable("testNewRulesRoutableAction/webMethod1/");
    }

    @Test
    public void testNewRulesRoutableAction_webMethod3Through5() throws Exception {
        assertReachable("testNewRulesRoutableAction/webMethod3/");
        assertReachable("testNewRulesRoutableAction/webMethod4/");
        assertReachable("testNewRulesRoutableAction/webMethod5/");
    }

    @Test
    public void testNewRulesRoutableAction_webMethod2() throws Exception {
        assertNotReachable("testNewRulesRoutableAction/webMethod2/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @TestExtension
    public static class TestNewRulesNonroutableAction extends AbstractUnprotectedRootAction {
        @StaplerNotDispatchable
        public void doWebMethod1() { replyOk(); }

        @StaplerNotDispatchable
        @WebMethod(name = "webMethod2")
        public void doWebMethod2() { replyOk(); }
    }

    @Test
    public void testNewRulesNonroutableAction_webMethod1() throws Exception {
        assertNotReachable("testNewRulesNonroutableAction/webMethod1/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testNewRulesNonroutableAction_webMethod2() throws Exception {
        // priority of negative over positive
        assertNotReachable("testNewRulesNonroutableAction/webMethod2/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }
}
