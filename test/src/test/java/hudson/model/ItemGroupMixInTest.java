/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.diagnosis.OldDataMonitor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

public class ItemGroupMixInTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-20951")
    @LocalData
    @Test public void xmlFileReadCannotResolveClassException() {
        MockFolder d = r.jenkins.getItemByFullName("d", MockFolder.class);
        assertNotNull(d);
        Collection<TopLevelItem> items = d.getItems();
        assertEquals(1, items.size());
        assertEquals("valid", items.iterator().next().getName());
    }

  /**
   * This test unit makes sure that if part of the config.xml file is
   * deleted it will still load everything else inside the folder.
   * The test unit expects an IOException is thrown, and the one failed
   * job fails to load.
   */
  @Issue("JENKINS-22811")
  @Test
  public void xmlFileFailsToLoad() throws Exception {
    MockFolder folder = r.createFolder("folder");
    assertNotNull(folder);

    AbstractProject project = folder.createProject(FreeStyleProject.class, "job1");
    AbstractProject project2 = folder.createProject(FreeStyleProject.class, "job2");
    AbstractProject project3 = folder.createProject(FreeStyleProject.class, "job3");

    File configFile = project.getConfigFile().getFile();

    List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8).subList(0, 5);
    configFile.delete();

    // Remove half of the config.xml file to make "invalid" or fail to load
    Files.writeString(configFile.toPath(), lines.toString(), StandardCharsets.UTF_8);
    for (int i = lines.size() / 2; i < lines.size(); i++) {
      Files.writeString(configFile.toPath(), lines.get(i), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    // Reload Jenkins.
    r.jenkins.reload();

    // Folder
    assertNotNull("Folder failed to load.", r.jenkins.getItemByFullName("folder"));
    assertNull("Job should have failed to load.", r.jenkins.getItemByFullName("folder/job1"));
    assertNotNull("Other job in folder should have loaded.", r.jenkins.getItemByFullName("folder/job2"));
    assertNotNull("Other job in folder should have loaded.", r.jenkins.getItemByFullName("folder/job3"));
  }

  /**
   * This test unit makes sure that jobs that contain bad get*Action methods will continue to
   * load the project.
   */
  @LocalData
  @Issue("JENKINS-22811")
  @Test
  public void xmlFileReadExceptionOnLoad() {
    MockFolder d = r.jenkins.getItemByFullName("d", MockFolder.class);
    assertNotNull(d);
    Collection<TopLevelItem> items = d.getItems();
    assertEquals(5, items.size());
  }

  @TestExtension
  public static class MockBuildWrapperThrowsError extends BuildWrapper {
    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
      throw new NullPointerException();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
      @Override
      public boolean isApplicable(AbstractProject<?, ?> item) {
        return true;
      }
    }
  }

  @TestExtension
  public static class MockBuilderThrowsError extends Builder {
    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
      throw new NullPointerException();
    }

    @Extension public static final Descriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends BuildStepDescriptor {
      @Override
      public boolean isApplicable(Class jobType) {
        return false;
      }
    }
  }

  @TestExtension
  public static class MockBuildTriggerThrowsError extends Trigger {
    @Override
    public Collection<? extends Action> getProjectActions() {
      throw new NullPointerException();
    }

    @Extension public static final Descriptor DESCRIPTOR = new BuildTrigger.DescriptorImpl();
  }

  @TestExtension
  public static class MockPublisherThrowsError extends Publisher {
    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject project) {
      throw new NullPointerException();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
      return null;
    }

    @Extension public static final Descriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends BuildStepDescriptor {
      @Override
      public boolean isApplicable(Class jobType) {
        return false;
      }
    }
  }

    @Test public void createProjectFromXMLShouldNoCreateEntities() throws IOException {

        final String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<!DOCTYPE project[\n" +
                "  <!ENTITY foo SYSTEM \"file:///\">\n" +
                "]>\n" +
                "<project>\n" +
                "  <actions/>\n" +
                "  <description>&foo;</description>\n" +
                "  <keepDependencies>false</keepDependencies>\n" +
                "  <properties/>\n" +
                "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                "  <canRoam>true</canRoam>\n" +
                "  <triggers/>\n" +
                "  <builders/>\n" +
                "  <publishers/>\n" +
                "  <buildWrappers/>\n" +
                "</project>";

        Item foo = r.jenkins.createProjectFromXML("foo", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        // if no exception then JAXP is swallowing these - so there should be no entity in the description.
        assertThat(Items.getConfigFile(foo).asString(), containsString("<description/>"));
    }

  @Issue("JENKINS-61956")
  @Test
  public void copy_checkGoodName() throws IOException {
    final String goodName = "calvin-jenkins";
    final String badName = "calvin@jenkins";

    Project goodProject = r.jenkins.createProject(FreeStyleProject.class, goodName);

    Failure exception = assertThrows(Failure.class, () -> r.jenkins.copy(goodProject, badName));
    assertEquals(exception.getMessage(), Messages.Hudson_UnsafeChar("@"));
  }

  @Issue("JENKINS-61956")
  @Test
  public void createProject_checkGoodName() {
    final String badName = "calvin@jenkins";

    Failure exception = assertThrows(Failure.class, () -> r.jenkins.createProject(MockFolder.class, badName));
    assertEquals(exception.getMessage(), Messages.Hudson_UnsafeChar("@"));
  }

  @Issue("JENKINS-61956")
  @Test
  public void createProjectFromXML_checkGoodName() {
    final String badName = "calvin@jenkins";

    final String xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<!DOCTYPE project[\n" +
            "  <!ENTITY foo SYSTEM \"file:///\">\n" +
            "]>\n" +
            "<project>\n" +
            "  <actions/>\n" +
            "  <description>&foo;</description>\n" +
            "  <keepDependencies>false</keepDependencies>\n" +
            "  <properties/>\n" +
            "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
            "  <canRoam>true</canRoam>\n" +
            "  <triggers/>\n" +
            "  <builders/>\n" +
            "  <publishers/>\n" +
            "  <buildWrappers/>\n" +
            "</project>";

    Failure exception = assertThrows(Failure.class, () -> r.jenkins.createProjectFromXML(badName, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    assertEquals(exception.getMessage(), Messages.Hudson_UnsafeChar("@"));
  }

  @Issue("SECURITY-1923")
  @Test
  public void doCreateItemWithValidXmlAndBadField() throws Exception {
    final String CREATOR = "create_user";

    r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
    MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
    mas.grant(Item.CREATE, Item.CONFIGURE, Item.READ, Jenkins.READ)
            .everywhere()
            .to(CREATOR);
    r.jenkins.setAuthorizationStrategy(mas);

    JenkinsRule.WebClient wc = r.createWebClient();
    wc.login(CREATOR);
    WebRequest req = new WebRequest(wc.createCrumbedUrl("createItem?name=testProject"), HttpMethod.POST);
    req.setAdditionalHeader("Content-Type", "application/xml");
    req.setRequestBody(VALID_XML_BAD_FIELD_USER_XML);

    FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(req));
    // This really shouldn't return 500, but that's what it does now.
    assertThat(e.getStatusCode(), equalTo(500));

    OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
    Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();

    assertThat(data.size(), equalTo(0));

    odm.doDiscard(null, null);

    User.AllUsers.scanAll();
    boolean createUser = false;
    User badUser = User.getById("foo", createUser);

    assertNull("Should not have created user.", badUser);
  }

  private static final String VALID_XML_BAD_FIELD_USER_XML =
          "<hudson.model.User>\n" +
                  "  <id>foo</id>\n" +
                  "  <fullName>Foo User</fullName>\n" +
                  "  <badField/>\n" +
                  "</hudson.model.User>\n";
}
