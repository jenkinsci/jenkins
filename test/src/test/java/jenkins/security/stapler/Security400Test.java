/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.security.stapler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AsyncPeriodicWork;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.PeriodicWork;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.tasks.Builder;
import hudson.triggers.Trigger;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.WebApp;

/**
 * To check the previous behavior you can use:
 * <pre>
 * {@link org.kohsuke.stapler.MetaClass#LEGACY_WEB_METHOD_MODE} = true;
 * {@link org.kohsuke.stapler.MetaClass#LEGACY_GETTER_MODE} = true;
 * </pre>
 */
@Issue("SECURITY-400")
public class Security400Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static boolean filteredDoActionTriggered = false;

    @Before
    public void prepareFilterListener() {
        WebApp webApp = WebApp.get(j.jenkins.getServletContext());
        webApp.setFilteredDoActionTriggerListener((f, req, rsp, node) -> {
            filteredDoActionTriggered = true;
            return false;
        });
        webApp.setFilteredGetterTriggerListener((f, req, rsp, node, expression) -> {
            filteredDoActionTriggered = true;
            return false;
        });
    }

    @After
    public void resetFilter() {
        filteredDoActionTriggered = false;
    }

    private void assertRequestWasBlockedAndResetFlag() {
        assertTrue("No request was blocked", filteredDoActionTriggered);
        filteredDoActionTriggered = false;
    }

    private void assertRequestWasNotBlocked() {
        assertFalse("There was at least a request that was blocked", filteredDoActionTriggered);
    }

    @Test
    @Issue("SECURITY-391")
    public void asyncDoRun() throws Exception {
        j.createWebClient().assertFails("extensionList/" + AsyncPeriodicWork.class.getName() + "/" + Work.class.getName() + "/run", HttpURLConnection.HTTP_NOT_FOUND);
        Thread.sleep(1000); // give the thread a moment to finish
        assertFalse("should never have run", ran);
    }

    private static boolean ran;

    @TestExtension("asyncDoRun")
    public static class Work extends AsyncPeriodicWork {
        public Work() {
            super("Test");
        }

        @Override
        public long getRecurrencePeriod() {
            return Long.MAX_VALUE; // do not run after init()
        }

        @Override
        protected void execute(TaskListener listener) throws IOException, InterruptedException {
            ran = true;
        }
    }

    @Test
    @Issue("SECURITY-397")
    // particular case of SECURITY-391
    public void triggerCronDoRun() throws Exception {
        j.createWebClient().assertFails("extensionList/" + PeriodicWork.class.getName() + "/" + Trigger.Cron.class.getName() + "/run", HttpURLConnection.HTTP_NOT_FOUND);
        assertRequestWasBlockedAndResetFlag();
    }

    /**
     * replacement of "computers/0/executors/0/contextClassLoader/context/handlers/0/sessionManager/stop" attack
     */
    @Test
    @Issue("SECURITY-404")
    public void avoidDangerousAccessToSession() throws Exception {
        j.jenkins.setCrumbIssuer(null);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(
                new MockAuthorizationStrategy()
                        .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                        .grant(Jenkins.READ).everywhere().to("user")
        );

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        wc.login("admin");

        JenkinsRule.WebClient wc2 = j.createWebClient();
        wc2.getOptions().setThrowExceptionOnFailingStatusCode(false);
        wc2.login("user");

        Page page;

        page = wc.goTo("whoAmI/api/xml/", null);
        System.out.println(page.getWebResponse().getContentAsString());
        assertThat(page.getWebResponse().getContentAsString(), containsString("<anonymous>false</anonymous>"));

        page = wc2.goTo("whoAmI/api/xml/", null);
        System.out.println(page.getWebResponse().getContentAsString());
        assertThat(page.getWebResponse().getContentAsString(), containsString("<anonymous>false</anonymous>"));

        assertRequestWasNotBlocked();

        // the doXxx fix prevents the doStop to be executed
        // and in addition the getXxx fix prevents the getContextHandler to be used as navigation

        // the first beans/0 return the HashedSession
        // the second beans/0 return the HashSessionManager
        page = wc2.goTo("adjuncts/<randomString>/webApp/context/contextHandler/beans/0/beans/0/stop", null);
        // other possible path
        // page = wc.goTo("adjuncts/<randomString>/webApp/someStapler/currentRequest/session/servletContext/contextHandler/beans/0/beans/0/stop", null);
        // page = wc.goTo("adjuncts/<randomString>/webApp/someStapler/currentRequest/servletContext/contextHandler/beans/0/beans/0/stop", null);

//        assertEquals(404, page.getWebResponse().getStatusCode());
//        assertRequestWasBlockedAndResetFlag();
        // getWebApp is now forbidden
        assertEquals(403, page.getWebResponse().getStatusCode());

        // if the call was successful, both are disconnected and anonymous would have been true

        page = wc.goTo("whoAmI/api/xml/", null);
        System.out.println(page.getWebResponse().getContentAsString());
        assertThat(page.getWebResponse().getContentAsString(), containsString("<anonymous>false</anonymous>"));

        page = wc2.goTo("whoAmI/api/xml/", null);
        System.out.println(page.getWebResponse().getContentAsString());
        assertThat(page.getWebResponse().getContentAsString(), containsString("<anonymous>false</anonymous>"));

        assertRequestWasNotBlocked();

        // similar approach but different impact:
        // can put null into desired session key (no impact yet)
        // session impl. is HashedSession
        // page = wc.goTo("adjuncts/<randomString>/webApp/someStapler/currentRequest/session/putOrRemove/ACEGI_SECURITY_CONTEXT/", null);
    }

    @Test
    @Issue("SECURITY-404")
    public void ensureDoStopStillReachable() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        // used as a reference passed to the build step
        AtomicInteger atomicResult = new AtomicInteger(0);
        FreeStyleProject p = j.createFreeStyleProject();

        final Semaphore semaphore = new Semaphore(0);

        p.getBuildersList().add(new SemaphoredBuilder(semaphore, atomicResult));

        // to be sure to reach the correct one
        j.jenkins.setNumExecutors(1);

        { // preliminary test, calling the stop method without any executor results in 404
            WebRequest request = new WebRequest(new URI(j.getURL() + "computers/0/executors/0/stop").toURL(), HttpMethod.POST);
            Page page = wc.getPage(request);
            assertEquals(404, page.getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();
        }

        { // first try, we let the build finishes normally
            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            futureBuild.waitForStart();

            // let the build finishes
            semaphore.release(1);
            j.assertBuildStatus(Result.SUCCESS, futureBuild);
            assertEquals(1, atomicResult.get());
        }

        { // second try, we need to reach the stop method in executor to interrupt the build
            atomicResult.set(0);
            assertEquals(0, atomicResult.get());
            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            FreeStyleBuild build = futureBuild.waitForStart();

            // we need to wait until the SemaphoreBuilder is running (blocked) otherwise the job is ABORTED not FAILURE
            j.waitForMessage(SemaphoredBuilder.START_MESSAGE, build);

            WebRequest request = new WebRequest(new URI(j.getURL() + "computers/0/executors/0/stop").toURL(), HttpMethod.POST);
            Page page = wc.getPage(request);
            assertEquals(404, page.getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();

            j.assertBuildStatus(Result.FAILURE, futureBuild);
            assertEquals(3, atomicResult.get());
        }
    }

    /*
     * Similar to ensureDoStopStillReachable()}, but tests Executor.doStopBuild(String) instead of Executor.doStop().
     * Implemented here (instead of ExecutorTest) for convenience (uses SemaphoredBuilder).
     */
    @Test
    @Issue("JENKINS-59656")
    public void ensureDoStopBuildWorks() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false); // we expect 404
        wc.getOptions().setPrintContentOnFailingStatusCode(false); // be less verbose

        // gives access to the build result code
        final AtomicInteger atomicResult = new AtomicInteger(0);
        // blocks execution of the build step
        final Semaphore semaphore = new Semaphore(0);
        // the test project with a semaphored build step
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new SemaphoredBuilder(semaphore, atomicResult));

        // to be sure to reach the correct one
        j.jenkins.setNumExecutors(1);

        { // preliminary test, calling stopBuild without any executor results in 404
            WebRequest request = new WebRequest(new URI(j.getURL() + "computers/0/executors/0/stopBuild").toURL(), HttpMethod.POST);
            Page page = wc.getPage(request);
            assertEquals(404, page.getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();
        }

        { // first try, we let the build finishes normally
            // reset semaphore and result code
            semaphore.drainPermits();
            atomicResult.set(0);

            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            futureBuild.waitForStart();

            // let the build finishes
            semaphore.release(1);

            j.assertBuildStatus(Result.SUCCESS, futureBuild);
            assertEquals(1, atomicResult.get());
        }

        { // second try, calling stopBuild without parameter interrupts the build (same as calling stop)
            // reset semaphore and result code
            semaphore.drainPermits();
            atomicResult.set(0);

            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            futureBuild.waitForStart();

            WebRequest request = new WebRequest(new URI(j.getURL() + "computers/0/executors/0/stopBuild").toURL(), HttpMethod.POST);
            Page page = wc.getPage(request);
            assertEquals(404, page.getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();

            // let the build finish quickly (if not interrupted already)
            semaphore.release(1);

            j.assertBuildStatus(Result.FAILURE, futureBuild);
            assertEquals(3, atomicResult.get()); // interrupted
        }

        { // third try, calling stopBuild with the right parameter interrupts the build
            // reset semaphore and result code
            semaphore.drainPermits();
            atomicResult.set(0);

            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            FreeStyleBuild build = futureBuild.waitForStart();
            String runExtId = URLEncoder.encode(build.getExternalizableId(), StandardCharsets.UTF_8);

            WebRequest request = new WebRequest(new URI(j.getURL() + "computers/0/executors/0/stopBuild?runExtId=" + runExtId).toURL(), HttpMethod.POST);
            Page page = wc.getPage(request);
            assertEquals(404, page.getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();

            // let the build finish quickly (if not interrupted already)
            semaphore.release(1);

            j.assertBuildStatus(Result.FAILURE, futureBuild);
            assertEquals(3, atomicResult.get()); // interrupted
        }

        { // fourth try, calling stopBuild with a parameter not matching build id doesn't interrupt the build
            // reset semaphore and result code
            semaphore.drainPermits();
            atomicResult.set(0);

            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            futureBuild.waitForStart();

            WebRequest request = new WebRequest(new URI(j.getURL() + "computers/0/executors/0/stopBuild?runExtId=whatever").toURL(), HttpMethod.POST);
            Page page = wc.getPage(request);
            assertEquals(404, page.getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();

            // let the build finishes
            semaphore.release(1);

            j.assertBuildStatus(Result.SUCCESS, futureBuild);
            assertEquals(1, atomicResult.get());
        }
    }

    @Test
    @Issue("SECURITY-404")
    public void anonCannotReadTextConsole() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        FullControlOnceLoggedInAuthorizationStrategy authorizationStrategy = new FullControlOnceLoggedInAuthorizationStrategy();
        authorizationStrategy.setAllowAnonymousRead(false);
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        FreeStyleProject p = j.createFreeStyleProject();

        Semaphore semaphore = new Semaphore(0);

        p.getBuildersList().add(new SemaphoredBuilder(semaphore, new AtomicInteger(0)));

        // to be sure to reach the correct one
        j.jenkins.setNumExecutors(1);

        { // preliminary test, calling the consoleText method without any executor results in 404
            Page page = wc.goTo("computers/0/executors/0/currentExecutable/consoleText", null);
            checkPageIsRedirectedToLogin(page);
            assertRequestWasNotBlocked();
        }

        { // as Connected User, we start the build and try to get the console, ensure current expected behavior still works
            wc.login("foo");

            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            futureBuild.waitForStart();

            Page page = wc.goTo("computers/0/executors/0/currentExecutable/consoleText", null);
            assertEquals(200, page.getWebResponse().getStatusCode());
            assertThat(page.getWebResponse().getContentAsString(), containsString(SemaphoredBuilder.START_MESSAGE));
            assertRequestWasNotBlocked();

            semaphore.release(1);
            j.assertBuildStatus(Result.SUCCESS, futureBuild);
        }

        { // as Anonymous, we start the build and try to get the console
            wc = j.createWebClient();
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            futureBuild.waitForStart();

            Page page = wc.goTo("computers/0/executors/0/currentExecutable/consoleText", null);
            checkPageIsRedirectedToLogin(page);
            assertThat(page.getWebResponse().getContentAsString(), not(containsString(SemaphoredBuilder.START_MESSAGE)));
            assertRequestWasNotBlocked();

            semaphore.release(1);
            j.assertBuildStatus(Result.SUCCESS, futureBuild);
        }
    }


    @Test
    @Issue("SECURITY-404")
    public void anonCannotAccessExecutorApi() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        FullControlOnceLoggedInAuthorizationStrategy authorizationStrategy = new FullControlOnceLoggedInAuthorizationStrategy();
        authorizationStrategy.setAllowAnonymousRead(false);
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        FreeStyleProject p = j.createFreeStyleProject();

        Semaphore semaphore = new Semaphore(0);

        p.getBuildersList().add(new SemaphoredBuilder(semaphore, new AtomicInteger(0)));

        // to be sure to reach the correct one
        j.jenkins.setNumExecutors(1);

        {
            Page page = wc.goTo("computers/0/executors/0/api/xml", null);
            checkPageIsRedirectedToLogin(page);
            assertRequestWasNotBlocked();
        }

        { // as Connected User, we start the build and can access the executor api
            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            futureBuild.waitForStart();

            wc.login("foo");
            Page page = wc.goTo("computers/0/executors/0/api/xml", null);
            assertEquals(200, page.getWebResponse().getStatusCode());
            assertThat(page.getWebResponse().getContentAsString(), containsString(p.getUrl()));
            assertRequestWasNotBlocked();

            semaphore.release(1);
            j.assertBuildStatus(Result.SUCCESS, futureBuild);

            wc = j.createWebClient();
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        }

        { // as Anonymous, we start the build and cannot access the executor api
            QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
            futureBuild.waitForStart();

            Page page = wc.goTo("computers/0/executors/0/api/xml", null);
            checkPageIsRedirectedToLogin(page);
            assertThat(page.getWebResponse().getContentAsString(), not(containsString(p.getUrl())));
            assertRequestWasNotBlocked();

            semaphore.release(1);
            j.assertBuildStatus(Result.SUCCESS, futureBuild);
        }
    }

    @Test
    @Issue("SECURITY-404")
    public void anonCannotAccessJenkinsItemMap() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        FreeStyleProject p = j.createFreeStyleProject();

        { // try to access /itemMap/<jobName>
            wc.login("foo");
            Page page = wc.goTo("itemMap/" + p.getName() + "/api/xml", null);
            assertEquals(404, page.getWebResponse().getStatusCode());
            assertThat(page.getWebResponse().getContentAsString(), not(containsString("<freeStyleProject")));
            assertRequestWasBlockedAndResetFlag();
        }
    }

    public static class SemaphoredBuilder extends Builder {
        private static final String START_MESSAGE = "job started, will try to acquire one permit";
        private transient Semaphore semaphore;
        private transient AtomicInteger atomicInteger;

        SemaphoredBuilder(Semaphore semaphore, AtomicInteger atomicInteger) {
            this.semaphore = semaphore;
            this.atomicInteger = atomicInteger;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            try {
                listener.getLogger().println(START_MESSAGE);
                boolean result = semaphore.tryAcquire(20, TimeUnit.SECONDS);
                if (result) {
                    listener.getLogger().println("permit acquired");
                    atomicInteger.set(1);
                    return true;
                } else {
                    atomicInteger.set(2);
                    return false;
                }
            } catch (InterruptedException e) {
                atomicInteger.set(3);
                return false;
            }
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Builder> {}
    }

    // currently there is no other way to reach logRecorderManager in core / or plugin
    @Test
    @Issue("SECURITY-471")
    public void ensureLogRecordManagerAccessibleOnlyByAdmin() throws Exception {
        j.jenkins.setCrumbIssuer(null);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(
                new MockAuthorizationStrategy()
                        .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                        .grant(Jenkins.READ).everywhere().to("user")
        );

        String logNameForAdmin = "testLoggerAdmin";
        String logNameForUser = "testLoggerUser";

        { // admin can do everything
            JenkinsRule.WebClient wc = j.createWebClient();
            wc.login("admin");
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

            // ensure the logger does not exist before the creation
            assertEquals(404, wc.goTo("log/" + logNameForAdmin + "/autoCompleteLoggerName/?value=a", null).getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();

            WebRequest request = new WebRequest(new URI(j.getURL() + "log/newLogRecorder/?name=" + logNameForAdmin).toURL(), HttpMethod.POST);

            wc.getOptions().setRedirectEnabled(false);
            Page page = wc.getPage(request);
            assertEquals(302, page.getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();

            // after creation the logger exists
            j.assertGoodStatus(wc.goTo("log/" + logNameForAdmin + "/autoCompleteLoggerName/?value=a", null));
            assertRequestWasNotBlocked();

            assertEquals(404, wc.goTo("log/" + "nonExistingName" + "/autoCompleteLoggerName/?value=a", null).getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();
        }

        { // user is blocked
            JenkinsRule.WebClient wc = j.createWebClient();
            wc.login("user");
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

            // no right to check the existence of a logger
            assertEquals(403, wc.goTo("log/" + logNameForUser + "/autoCompleteLoggerName/?value=a", null).getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();

            WebRequest request = new WebRequest(new URI(j.getURL() + "log/newLogRecorder/?name=" + logNameForUser).toURL(), HttpMethod.POST);

            wc.getOptions().setRedirectEnabled(false);
            Page page = wc.getPage(request);
            assertEquals(403, page.getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();

            // after the failed attempt, the logger is not created
            assertEquals(403, wc.goTo("log/" + logNameForUser + "/autoCompleteLoggerName/?value=a", null).getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();
        }

        { // admin can check the non-existence after user failed creation also

            JenkinsRule.WebClient wc = j.createWebClient();
            wc.login("admin");
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

            // ensure the logger was not created by the user (check in case the request returned 403 but created the logger silently)
            assertEquals(404, wc.goTo("log/" + logNameForUser + "/autoCompleteLoggerName/?value=a", null).getWebResponse().getStatusCode());
            assertRequestWasNotBlocked();
        }
    }

    @Test
    public void anonCannotHaveTheListOfUsers() throws Exception {
        j.jenkins.setCrumbIssuer(null);

        FullControlOnceLoggedInAuthorizationStrategy authorizationStrategy = new FullControlOnceLoggedInAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);

        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        securityRealm.createAccount("admin", "admin");
        securityRealm.createAccount("secretUser", "secretUser");

        { // admin should have access to the user list
            JenkinsRule.WebClient wc = j.createWebClient();
            wc.login("admin");
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

            Page page = wc.goTo("securityRealm");
            assertEquals(200, page.getWebResponse().getStatusCode());
            assertThat(page.getWebResponse().getContentAsString(), containsString("secretUser"));
            assertRequestWasNotBlocked();
        }

        // with or without the anonymousRead, anonymous are not allowed to have access to
        // list of users in securityRealm
        authorizationStrategy.setAllowAnonymousRead(true);
        { // without any read permission the anon have access to the user list
            JenkinsRule.WebClient wc = j.createWebClient();
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
            wc.getOptions().setRedirectEnabled(false);

            Page page = wc.goTo("securityRealm/", null);
            checkPageIsRedirectedToLogin(page);
            assertThat(page.getWebResponse().getContentAsString(), not(containsString("secretUser")));
            assertRequestWasNotBlocked();
        }

        authorizationStrategy.setAllowAnonymousRead(false);
        { // and with restriction, the anonymous users cannot read the user list
            JenkinsRule.WebClient wc = j.createWebClient();
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
            wc.getOptions().setRedirectEnabled(false);

            Page page = wc.goTo("securityRealm/", null);
            checkPageIsRedirectedToLogin(page);
            assertThat(page.getWebResponse().getContentAsString(), not(containsString("secretUser")));
            assertRequestWasNotBlocked();
        }
    }

    @Test
    @Issue("SECURITY-722")
    public void noAccessToAllUsers() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        securityRealm.createAccount("admin", "admin");

        j.jenkins.setAuthorizationStrategy(
                new MockAuthorizationStrategy()
                        .grant(Jenkins.ADMINISTER).everywhere().to("admin")
        );

        { // neither anon have access to the allUsers end point
            JenkinsRule.WebClient wc = j.createWebClient();
            // anonymous user
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

            Page page = wc.goTo("securityRealm/allUsers/" + 0 + "/descriptorByName/jenkins.security.ApiTokenProperty/help/apiToken/");
            assertEquals(404, page.getWebResponse().getStatusCode());
            assertRequestWasBlockedAndResetFlag();
        }

        { // nor the admin have that access
            JenkinsRule.WebClient wc = j.createWebClient();
            wc.login("admin");
            wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

            Page page = wc.goTo("securityRealm/allUsers/" + 0 + "/descriptorByName/jenkins.security.ApiTokenProperty/help/apiToken/");
            assertEquals(404, page.getWebResponse().getStatusCode());
            assertRequestWasBlockedAndResetFlag();
        }
    }

    // // does not work in 2.60 since the method was added in 2.91+
    // String newLogin = "newUser";
    // j.createWebClient().goTo("securityRealm/allUsers/0/orCreateByIdOrFullName/" + newLogin + "/");

    private void checkPageIsRedirectedToLogin(Page page) {
        assertEquals(200, page.getWebResponse().getStatusCode());
        assertThat(page.getUrl().getPath(), containsString("login"));
        assertThat(page.getUrl().getQuery(), containsString("from"));
    }
}
