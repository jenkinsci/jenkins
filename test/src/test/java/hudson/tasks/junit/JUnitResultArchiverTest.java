package hudson.tasks.junit;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class JUnitResultArchiverTest extends HudsonTestCase {

	public static final class TouchBuilder extends Builder implements Serializable {
		@Override
		public boolean perform(AbstractBuild<?, ?> build,
				Launcher launcher, BuildListener listener)
				throws InterruptedException, IOException {
			for (FilePath f: build.getWorkspace().list()) {
				f.touch(System.currentTimeMillis());
			}
			return true;
		}
	}

	private FreeStyleProject project;
	private JUnitResultArchiver archiver;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		project = createFreeStyleProject("junit");
		archiver = new JUnitResultArchiver("*.xml");
		project.getPublishersList().add(archiver);
		
		project.getBuildersList().add(new TouchBuilder());
	}
	
	@LocalData
	public void testBasic() throws Exception {
		FreeStyleBuild build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
		
		assertTestResults(build);
		
		WebClient wc =new WebClient();
		wc.getPage(project); // project page
		wc.getPage(build); // build page
		wc.getPage(build, "testReport");  // test report
		wc.getPage(build, "testReport/hudson.security"); // package
		wc.getPage(build, "testReport/hudson.security/HudsonPrivateSecurityRealmTest/"); // class
		wc.getPage(build, "testReport/hudson.security/HudsonPrivateSecurityRealmTest/testDataCompatibilityWith1_282/"); // method
		

	}

	private void assertTestResults(FreeStyleBuild build) {
		TestResultAction testResultAction = build.getAction(TestResultAction.class);
		assertNotNull("no TestResultAction", testResultAction);
		
		TestResult result = testResultAction.getResult();
		assertNotNull("no TestResult", result);
		
		assertEquals("should have 1 failing test", 1, testResultAction.getFailCount());
		assertEquals("should have 1 failing test", 1, result.getFailCount());
		
		assertEquals("should have 132 total tests", 132, testResultAction.getTotalCount());
		assertEquals("should have 132 total tests", 132, result.getTotalCount());
	}
	
	@LocalData
	public void testPersistence() throws Exception {
		project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
		
		reloadHudson();
		
		FreeStyleBuild build = project.getBuildByNumber(1);
		
		assertTestResults(build);
	}

	private void reloadHudson() throws NoSuchMethodException,
			IllegalAccessException, InvocationTargetException {
		Method m = Hudson.class.getDeclaredMethod("load");
		m.setAccessible(true);
		m.invoke(hudson);
		
		project = (FreeStyleProject) hudson.getItem("junit");
	}
	
	@LocalData
	public void testSetDescription() throws Exception {
		FreeStyleBuild build = project.scheduleBuild2(0).get(10, TimeUnit.SECONDS);
		
		CaseResult caseResult = build.getAction(TestResultAction.class).getFailedTests().get(0);
		String url = build.getUrl() + "/testReport/" + caseResult.getRelativePathFrom(caseResult.getTestResult());
		
		testSetDescription(url, caseResult);
		
		ClassResult classResult = caseResult.getParent();
		url = build.getUrl() + "/testReport/" + classResult.getParent().getSafeName() + "/" + classResult.getSafeName();
		testSetDescription(url, classResult);
		
		PackageResult packageResult = classResult.getParent();
		url = build.getUrl() + "/testReport/" + classResult.getParent().getSafeName();
		testSetDescription(url, packageResult);
		
	}

	private void testSetDescription(String url, TestObject object)
			throws IOException, SAXException, Exception {
		HtmlPage page = new WebClient().goTo(url);
		
		page.getAnchorByHref("editDescription").click();
		HtmlForm form = findForm(page, "submitDescription");
		form.getTextAreaByName("description").setText("description");
		submit(form);
		
		assertEquals("description", object.getDescription());
	}
	
	private HtmlForm findForm(HtmlPage page, String action) {
		for (HtmlForm form: page.getForms()) {
			if (action.equals(form.getActionAttribute())) {
				return form;
			}
		}
		fail("no form found");
		return null;
	}
}
