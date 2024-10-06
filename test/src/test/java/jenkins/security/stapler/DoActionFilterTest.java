package jenkins.security.stapler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.CapturedParameterNames;
import org.kohsuke.stapler.CrumbIssuer;
import org.kohsuke.stapler.Header;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.LimitedTo;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.RequestImpl;
import org.kohsuke.stapler.ResponseImpl;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.interceptor.RespondSuccess;
import org.kohsuke.stapler.json.JsonBody;
import org.kohsuke.stapler.json.JsonResponse;
import org.kohsuke.stapler.json.SubmittedForm;
import org.kohsuke.stapler.verb.DELETE;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;
import org.kohsuke.stapler.verb.PUT;

/**
 * To check the previous behavior you can use:
 * <pre>
 * {@link org.kohsuke.stapler.MetaClass#LEGACY_WEB_METHOD_MODE} = true;
 * </pre>
 * It will disable the usage of {@link DoActionFilter}
 */
@Issue("SECURITY-400")
public class DoActionFilterTest extends StaplerAbstractTest {

    @TestExtension
    public static class TestAccessModifierUrl extends AbstractUnprotectedRootAction {
        public TestAccessModifier getPublic() {
            return new TestAccessModifier();
        }

        protected TestAccessModifier getProtected() {
            return new TestAccessModifier();
        }

        TestAccessModifier getInternal() {
            return new TestAccessModifier();
        }

        private TestAccessModifier getPrivate() {
            return new TestAccessModifier();
        }

        public static class TestAccessModifier {
            @GET
            public String doValue() {
                return "hello";
            }
        }
    }

    @Test
    public void testProtectedMethodDispatch() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            try {
                wc.goTo("testAccessModifierUrl/public/value", null);
            } catch (FailingHttpStatusCodeException e) {
                throw new AssertionError("should have access to a public method", e);
            }
            FailingHttpStatusCodeException x = assertThrows("should not have allowed protected access", FailingHttpStatusCodeException.class, () -> wc.goTo("testAccessModifierUrl/protected/value", null));
            assertEquals(HttpServletResponse.SC_NOT_FOUND, x.getStatusCode());
            x = assertThrows("should not have allowed internal access", FailingHttpStatusCodeException.class, () -> wc.goTo("testAccessModifierUrl/internal/value", null));
            assertEquals(HttpServletResponse.SC_NOT_FOUND, x.getStatusCode());
            x = assertThrows("should not have allowed private access", FailingHttpStatusCodeException.class, () -> wc.goTo("testAccessModifierUrl/private/value", null));
            assertEquals(HttpServletResponse.SC_NOT_FOUND, x.getStatusCode());
        }
    }

    //================================= doXxx methods =================================

    @TestExtension
    public static class TestNewRulesOk extends AbstractUnprotectedRootAction {
        /*
         * Method signature
         */

        public static void doStaticWithRequest(StaplerRequest2 request) { replyOk(); }

        public void doWithRequest(StaplerRequest2 request) { replyOk(); }

        public void doWithHttpRequest(HttpServletRequest request) { replyOk(); }

        // the return type is not taken into consideration if it's not a HttpResponse, it will not prevent the method
        // to be considered as a web method
        public String doWithRequestAndReturnString(StaplerRequest2 request) { return "ok"; }

        public void doWithResponse(StaplerResponse2 response) { replyOk(); }

        public void doWithHttpResponse(HttpServletResponse response) { replyOk(); }

        public void doWithThrowHttpResponseException() throws HttpResponses.HttpResponseException { replyOk(); }

        // special cases, child of above classes, normally reachable, as it satisfies the contract
        // that requires to throw an exception that is an HttpResponseException
        public void doWithThrowHttpResponseExceptionChild() throws HttpResponseExceptionChild { replyOk(); }

        // the declared exception just has to implement HttpResponse
        public void doWithThrowExceptionImplementingOnlyHttpResponse() throws ExceptionImplementingOnlyHttpResponse { replyOk(); }

        public void doWithThrowOtherException() throws IOException { replyOk(); }

        public HttpResponse doWithReturnHttpResponse() { return HttpResponses.text("ok"); }

        public HttpResponseChild doWithReturnHttpResponseChild() { return new HttpResponseChild(); }

        /*
         * Method annotations
         */

        @WebMethod(name = "webMethodUrl")
        public void doWebMethod() { replyOk(); }

        // not requiring to have doXxx when using WebMethod
        @WebMethod(name = "webMethodUrl2")
        public void webMethod() { replyOk(); }

        @GET
        public void doAnnotatedGet() { replyOk(); }

        @POST
        public void doAnnotatedPost() { replyOk(); }

        @PUT
        public void doAnnotatedPut() { replyOk(); }

        @DELETE
        public void doAnnotatedDelete() { replyOk(); }

        @RequirePOST
        public void doAnnotatedRequirePost() { replyOk(); }

        @JavaScriptMethod
        public void annotatedJavaScriptScriptMethod() { replyOk(); }

        @RespondSuccess
        public void doAnnotatedResponseSuccess() { replyOk(); }

        @JsonResponse // does not support list
        public Map<String, Object> doAnnotatedJsonResponse() {
            return Map.of("a", "b");
        }

        @LimitedTo("admin")
        public void doAnnotatedLimitedTo() { replyOk(); }

        /*
         * Parameter annotation
         */

        public void doAnnotatedParamQueryParameter(@QueryParameter String value) { replyOk(); }

        public void doAnnotatedParamAncestorInPath(@AncestorInPath DoActionFilterTest parent) { replyOk(); }

        public void doAnnotatedParamHeader(@Header("test-header") String testHeader) { replyOk(); }

        public void doAnnotatedParamJsonBody(@JsonBody Map<String, String> names) { replyOk(); }

        public void doAnnotatedParamSubmittedForm(@SubmittedForm JSONObject form) { replyOk(); }

        /*
         * Parameter annotation
         */

        public void do_CallMeBecauseOfMyUnderscore(StaplerRequest2 request) { replyOk(); }

        public void do$CallMeBecauseOfMyDollar(StaplerRequest2 request) { replyOk(); }
    }

    public static class HttpResponseChild implements HttpResponse {
        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
            replyOk();
        }
    }

    public abstract static class HttpResponseExceptionChild extends HttpResponses.HttpResponseException {
    }

    public static class ExceptionImplementingOnlyHttpResponse extends RuntimeException implements HttpResponse {
        @Override
        public void generateResponse(StaplerRequest2 staplerRequest, StaplerResponse2 staplerResponse, Object o) throws IOException, ServletException {
            replyOk();
        }
    }

    //########### actual test methods ###########
    @Test
    public void testMethodSignatureOk_staticWithRequest() throws Exception {
        assertReachable("testNewRulesOk/staticWithRequest/");
    }

    @Test
    public void testMethodSignatureOk_withRequest() throws Exception {
        assertReachable("testNewRulesOk/withRequest/");
    }

    @Test
    public void testMethodSignatureOk_withRequestAndReturnString() throws Exception {
        assertReachable("testNewRulesOk/withRequestAndReturnString/");
    }

    @Test
    public void testMethodSignatureOk_withHttpRequest() throws Exception {
        assertReachable("testNewRulesOk/withHttpRequest/");
    }

    @Test
    public void testMethodSignatureOk_withHttpResponse() throws Exception {
        assertReachable("testNewRulesOk/withHttpResponse/");
    }

    @Test
    public void testMethodSignatureOk_withResponse() throws Exception {
        assertReachable("testNewRulesOk/withResponse/");
    }

    @Test
    public void testMethodSignatureOk_withThrowHttpResponseException() throws Exception {
        assertReachable("testNewRulesOk/withThrowHttpResponseException/");
    }

    @Test
    public void testMethodSignatureOk_withThrowHttpResponseExceptionChild() throws Exception {
        assertReachable("testNewRulesOk/withThrowHttpResponseExceptionChild/");
    }

    @Test
    public void testMethodSignatureOk_withThrowExceptionImplementingOnlyHttpResponse() throws Exception {
        assertReachable("testNewRulesOk/withThrowExceptionImplementingOnlyHttpResponse/");
    }

    @Test
    public void testMethodSignatureOk_withThrowOtherException() throws Exception {
        assertNotReachable("testNewRulesOk/withThrowOtherException/");
    }

    @Test
    public void testMethodSignatureOk_withReturnHttpResponse() throws Exception {
        assertReachable("testNewRulesOk/withReturnHttpResponse/");
    }

    @Test
    public void testMethodSignatureOk_withReturnHttpResponseChild() throws Exception {
        assertReachable("testNewRulesOk/withReturnHttpResponseChild/");
    }

    @Test
    public void testAnnotatedMethodOk_webMethodUrl() throws Exception {
        assertReachable("testNewRulesOk/webMethodUrl/");
    }

    @Test
    public void testAnnotatedMethodOk_webMethodUrl2() throws Exception {
        assertReachable("testNewRulesOk/webMethodUrl2/");
    }

    @Test
    public void testAnnotatedMethodOk_annotatedGet() throws Exception {
        assertReachable("testNewRulesOk/annotatedGet/");
    }

    @Test
    public void testAnnotatedMethodOk_annotatedPost() throws Exception {
        WebRequest settings = new WebRequest(new URL(j.getURL(), "testNewRulesOk/annotatedPost/"));
        settings.setHttpMethod(HttpMethod.POST);
        settings.setRequestBody("");
        assertReachableWithSettings(settings);
    }

    @Test
    public void testAnnotatedMethodOk_annotatedPut() throws Exception {
        WebRequest settings = new WebRequest(new URL(j.getURL(), "testNewRulesOk/annotatedPut/"));
        settings.setHttpMethod(HttpMethod.PUT);
        settings.setRequestBody("");
        assertReachableWithSettings(settings);
    }

    @Test
    public void testAnnotatedMethodOk_annotatedDelete() throws Exception {
        assertReachable("testNewRulesOk/annotatedDelete/", HttpMethod.DELETE);
    }

    @Test
    public void testAnnotatedMethodOk_annotatedRequirePost() throws Exception {
        WebRequest settings = new WebRequest(new URL(j.getURL(), "testNewRulesOk/annotatedRequirePost/"));
        settings.setHttpMethod(HttpMethod.POST);
        settings.setRequestBody("");
        assertReachableWithSettings(settings);
    }

    @Test
    public void testAnnotatedMethodOk_annotatedJavaScriptScriptMethod() throws Exception {
        webApp.setCrumbIssuer(new CrumbIssuer() {
            @Override
            public String issueCrumb(StaplerRequest2 request) {
                return "test";
            }

            @Override
            public void validateCrumb(StaplerRequest2 request, String submittedCrumb) {
                // no exception thrown = validated
            }
        });


        WebRequest settings = new WebRequest(new URL(j.getURL(), "testNewRulesOk/annotatedJavaScriptScriptMethod/"));
        settings.setAdditionalHeader("Content-Type", "application/x-stapler-method-invocation");
        settings.setHttpMethod(HttpMethod.POST);
        settings.setRequestBody(JSONArray.fromObject(Collections.emptyList()).toString());
        assertReachableWithSettings(settings);
    }

    @Test
    public void testAnnotatedMethodOk_annotatedResponseSuccess() throws Exception {
        assertReachable("testNewRulesOk/annotatedResponseSuccess/");
    }

    @Test
    public void testAnnotatedMethodOk_annotatedJsonResponse() throws Exception {
        WebRequest settings = new WebRequest(new URL(j.getURL(), "testNewRulesOk/annotatedJsonResponse/"));
        settings.setHttpMethod(HttpMethod.POST);
        settings.setRequestBody(JSONObject.fromObject(Collections.emptyMap()).toString());
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            Page page = wc.getPage(settings);
            assertEquals(200, page.getWebResponse().getStatusCode());
        }
    }

    @Test
    public void testAnnotatedMethodOk_annotatedLimitedTo() {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(new URL(j.getURL(), "testNewRulesOk/annotatedLimitedTo/")));
            assertEquals(500, e.getStatusCode());
            assertTrue(e.getResponse().getContentAsString().contains("Needs to be in role"));
        }
    }

    @Test
    public void testAnnotatedParameterOk_annotatedParamQueryParameter() throws Exception {
        // parameter is optional by default
        assertReachable("testNewRulesOk/annotatedParamQueryParameter/");
        assertReachable("testNewRulesOk/annotatedParamQueryParameter/?value=test");
    }

    @Test
    public void testAnnotatedParameterOk_annotatedParamAncestorInPath() throws Exception {
        assertReachable("testNewRulesOk/annotatedParamAncestorInPath/");
    }

    @Test
    public void testAnnotatedParameterOk_annotatedParamHeader() throws Exception {
        WebRequest settings = new WebRequest(new URL(j.getURL(), "testNewRulesOk/annotatedParamHeader/"));
        settings.setAdditionalHeader("test-header", "TestBrowser");
        assertReachableWithSettings(settings);
    }

    @Test
    public void testAnnotatedParameterOk_annotatedParamJsonBody() throws Exception {
        WebRequest settings = new WebRequest(new URL(j.getURL(), "testNewRulesOk/annotatedParamJsonBody/"));
        // WebClient forces us to use POST to have the possibility to send requestBody
        settings.setHttpMethod(HttpMethod.POST);
        settings.setAdditionalHeader("Content-Type", "application/json");
        settings.setRequestBody(JSONObject.fromObject(Map.of("name", "Test")).toString());
        assertReachableWithSettings(settings);
    }

    @Test
    public void testAnnotatedParameterOk_annotatedParamSubmittedForm() throws Exception {
        WebRequest settings = new WebRequest(new URL(j.getURL(), "testNewRulesOk/annotatedParamSubmittedForm/"));
        settings.setHttpMethod(HttpMethod.POST);

        settings.setRequestParameters(List.of(
                new NameValuePair(
                        "json",
                        JSONObject.fromObject(Map.of("name", "Test")).toString()
                )
        ));
        assertReachableWithSettings(settings);
    }

    @Test
    public void testOk__CallMeBecauseOfMyUnderscore() throws Exception {
        assertReachable("testNewRulesOk/_CallMeBecauseOfMyUnderscore/");
    }

    @Test
    public void testOk_$CallMeBecauseOfMyDollar() throws Exception {
        assertReachable("testNewRulesOk/$CallMeBecauseOfMyDollar/");
    }

    @TestExtension
    public static class TestNewRulesOkDynamic extends AbstractUnprotectedRootAction {
        // sufficiently magical name to be reached
        public void doDynamic() { replyOk(); }
    }


    @TestExtension
    public static class TestNewRulesOkIndex extends AbstractUnprotectedRootAction {
        // considered as index
        @WebMethod(name = "")
        public void methodWithoutNameEqualIndex() { replyOk(); }
    }

    @TestExtension
    public static class TestNewRulesOkDoIndex extends AbstractUnprotectedRootAction {
        public void doIndex() { replyOk(); }
    }

    @Test
    public void testSpecialCasesOk() throws Exception {
        assertReachable("testNewRulesOkDynamic/anyString/");
        assertReachable("testNewRulesOkIndex/");
        assertReachable("testNewRulesOkDoIndex/");
    }

    // those methods are accepted in legacy system but potentially dangerous
    @TestExtension
    public static class TestNewRulesNotOk extends AbstractUnprotectedRootAction {
        // do not respect the do[^a-z].* format
        public void dontCallMeBecauseOfMyDont(StaplerRequest2 request) { replyOk(); }

        // do not seem to be an expected web method, in case a developer has such methods,
        // addition of WebMethod annotation is sufficient
        public void doSomething() { replyOk(); }

        // returning a String is not sufficient to be considered as a web method
        public String doReturnString() { return "ok"; }

        // returning a super class of HttpResponse is not sufficient
        public Object doReturnObject() { return "ok"; }
    }

    @Test
    public void testNotOk_ntCallMeBecauseOfMyDont() throws Exception {
        assertNotReachable("testNewRulesNotOk/ntCallMeBecauseOfMyDont/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testNotOk_something() throws Exception {
        assertNotReachable("testNewRulesNotOk/something/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testNotOk_returnString() throws Exception {
        assertNotReachable("testNewRulesNotOk/returnString/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testNotOk_returnObject() throws Exception {
        assertNotReachable("testNewRulesNotOk/returnObject/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @TestExtension
    public static class TestNewRulesNotOkSpecialCases extends AbstractUnprotectedRootAction {
        public void doWithServletRequest(ServletRequest request) { replyOk(); }

        public void doWithServletResponse(ServletResponse response) { replyOk(); }

        // special cases, child of above classes
        public void doWithRequestImpl(RequestImpl request) { replyOk(); }

        public void doWithResponseImpl(ResponseImpl response) { replyOk(); }

        // special case to keep Groovy parameter name, but does not seem to indicate it's automatically a web method
        @CapturedParameterNames("req")
        public void doAnnotatedResponseSuccess(Object req) { replyOk(); }

//        // as mentioned in its documentation, it requires to have JavaScriptMethod, that has its own test
//        @JsonOutputFilter
//        public void doAnnotatedJsonOutputFilter() { replyOk(); }
    }

    @Test
    public void testNotOkSpecialCases_withServletRequest() throws Exception {
        assertNotReachable("testNewRulesNotOkSpecialCases/withServletRequest/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testNotOkSpecialCases_withServletResponse() throws Exception {
        assertNotReachable("testNewRulesNotOkSpecialCases/withServletResponse/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testNotOkSpecialCases_withRequestImpl() throws Exception {
        assertNotReachable("testNewRulesNotOkSpecialCases/withRequestImpl/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testNotOkSpecialCases_withResponseImpl() throws Exception {
        assertNotReachable("testNewRulesNotOkSpecialCases/withResponseImpl/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testNotOkSpecialCases_annotatedResponseSuccess() throws Exception {
        assertNotReachable("testNewRulesNotOkSpecialCases/annotatedResponseSuccess/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    // now JsonOutputFilter is accepted as a web method annotation
//    @Test
//    public void testNotOkSpecialCases_annotatedJsonOutputFilter() throws Exception {
//        assertNotReachable("testNewRulesNotOkSpecialCases/annotatedJsonOutputFilter/");
//        assertDoActionRequestWasBlockedAndResetFlag();
//    }

    //================================= class inheritance =================================

    public static class A {
        public void doNotAnnotatedAtAll() { replyOk(); }

        @WebMethod(name = "onlyAnnotatedInA")
        public void doOnlyAnnotatedInA() { replyOk(); }

        public void doOnlyAnnotatedInB() { replyOk(); }

        @WebMethod(name = "onlyAnnotatedInA-notOverrided")
        public void doOnlyAnnotatedInANotOverrided() { replyOk(); }

        @WebMethod(name = "annotatedButDifferent1")
        public void doAnnotatedButDifferent() { replyOk(); }
    }

    public static class B extends A {
        @Override
        public void doNotAnnotatedAtAll() { replyOk(); }

        @Override
        public void doOnlyAnnotatedInA() { replyOk(); }

        @Override
        @WebMethod(name = "onlyAnnotatedInB")
        public void doOnlyAnnotatedInB() { replyOk(); }

        // doOnlyAnnotatedInANotOverrided: not overrided

        @Override
        @WebMethod(name = "annotatedButDifferent2")
        public void doAnnotatedButDifferent() { replyOk(); }
    }

    @TestExtension
    public static class ABCase extends AbstractUnprotectedRootAction implements StaplerProxy {
        @Override
        public B getTarget() {
            return new B();
        }
    }

    @Test
    public void testClassInheritance_notAnnotatedAtAll() throws Exception {
        assertNotReachable("aBCase/notAnnotatedAtAll/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testClassInheritance_onlyAnnotatedInA() throws Exception {
        assertReachable("aBCase/onlyAnnotatedInA/");
    }

    @Test
    public void testClassInheritance_onlyAnnotatedInB() throws Exception {
        assertReachable("aBCase/onlyAnnotatedInB/");
    }

    @Test
    public void testClassInheritance_onlyAnnotatedInANotOverrided() throws Exception {
        assertNotReachable("aBCase/onlyAnnotatedInANotOverrided/");
    }

    @Test
    public void testClassInheritance_annotatedButDifferent1() throws Exception {
        // only the last webMethod annotation is used
        //TODO it breaks the Liskov substitutability
//        assertReachable("b/annotatedButDifferent1/");
        assertNotReachable("aBCase/annotatedButDifferent1/");
    }

    @Test
    public void testClassInheritance_annotatedButDifferent2() throws Exception {
        assertReachable("aBCase/annotatedButDifferent2/");
    }

    //================================= interface implementation =================================
    public interface I {
        void doNotAnnotated();

        @WebMethod(name = "annotatedBoth")
        void doAnnotatedBoth();

        @WebMethod(name = "annotatedOnlyI")
        void doAnnotatedOnlyI();

        void doAnnotatedOnlyJ();

        @WebMethod(name = "annotatedButDifferent1")
        void doAnnotatedButDifferent();
    }

    public static class J implements I {
        @Override
        public void doNotAnnotated() { replyOk(); }

        @Override
        @WebMethod(name = "annotatedBoth")
        public void doAnnotatedBoth() { replyOk(); }

        @Override
        public void doAnnotatedOnlyI() { replyOk(); }

        @Override
        @WebMethod(name = "annotatedOnlyJ")
        public void doAnnotatedOnlyJ() { replyOk(); }

        @Override
        @WebMethod(name = "annotatedButDifferent2")
        public void doAnnotatedButDifferent() { replyOk(); }
    }

    @TestExtension
    public static class IJCase extends AbstractUnprotectedRootAction implements StaplerProxy {
        @Override
        public J getTarget() {
            return new J();
        }
    }

    @Test
    public void testInterfaceImplementation_notAnnotated() throws Exception {
        assertNotReachable("iJCase/notAnnotated/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @Test
    public void testInterfaceImplementation_annotatedBoth() throws Exception {
        assertReachable("iJCase/annotatedBoth/");
    }

    @Test
    public void testInterfaceImplementation_annotatedOnlyI() throws Exception {
        assertReachable("iJCase/annotatedOnlyI/");
    }

    @Test
    public void testInterfaceImplementation_annotatedOnlyJ() throws Exception {
        assertReachable("iJCase/annotatedOnlyJ/");
    }

    @Test
    public void testInterfaceImplementation_annotatedButDifferent1() throws Exception {
        // only the last webMethod annotation is used
        //TODO it breaks the Liskov substitutability
        // assertReachable("j/annotatedButDifferent1/");
        assertNotReachable("iJCase/annotatedButDifferent1/");
    }

    @Test
    public void testInterfaceImplementation_annotatedButDifferent2() throws Exception {
        assertReachable("iJCase/annotatedButDifferent2/");
    }
}
