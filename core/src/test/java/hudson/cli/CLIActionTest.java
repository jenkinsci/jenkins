package hudson.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Optional;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import jenkins.websocket.WebSocketSession;
import jenkins.websocket.WebSockets;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class CLIActionTest {

  @Rule
  public JenkinsRule jenkinsRule = new JenkinsRule();

  private final CLIAction cliAction = new CLIAction();

  @Test
  public void testDoCommand() throws ServletException, IOException {
    Jenkins jenkins = jenkinsRule.getInstance();
    jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
    jenkinsRule.jenkins.setAuthorizationStrategy(
        new org.jvnet.hudson.test.MockAuthorizationStrategy()
            .grant(Jenkins.READ)
            .everywhere()
            .to("user"));

    StaplerRequest2 req = mock(StaplerRequest2.class);
    StaplerResponse2 rsp = mock(StaplerResponse2.class);

    when(req.getRestOfPath()).thenReturn("/testCommand");

    doAnswer(
        (Answer<Void>) invocation -> {
          int statusCode = invocation.getArgument(0);
          String message = invocation.getArgument(1);
          assertEquals("Expected 404 error", HttpServletResponse.SC_NOT_FOUND, statusCode);
          assertTrue("Expected 'No such command' message", message.contains("No such command"));
          return null;
        })
        .when(rsp)
        .sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());

    cliAction.doCommand(req, rsp);

    verify(rsp).sendError(HttpServletResponse.SC_NOT_FOUND, "No such command");
  }

  @Test
  public void testDoCommandWithParameter() throws ServletException, IOException {
    Jenkins jenkins = jenkinsRule.getInstance();

    jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
    jenkinsRule.jenkins.setAuthorizationStrategy(
        new MockAuthorizationStrategy()
            .grant(Jenkins.READ)
            .everywhere()
            .to("user"));

    StaplerRequest2 req = mock(StaplerRequest2.class);
    StaplerResponse2 rsp = mock(StaplerResponse2.class);
    RequestDispatcher dispatcher = mock(RequestDispatcher.class);

    when(req.getRestOfPath()).thenReturn("/add-job-to-view");
    when(req.getView(Optional.ofNullable(any()), anyString())).thenReturn(dispatcher);

    doNothing().when(dispatcher).forward(any(), any());

    cliAction.doCommand(req, rsp);

    verify(req).getRestOfPath();
    verify(req).getView(Optional.ofNullable(any()), anyString());
    verify(dispatcher).forward(any(), any());
  }

  @Test
  public void getIconFileName() {
    assertNull(cliAction.getIconFileName());
  }

  @Test
  public void getDisplayName() {
    assertEquals("Jenkins CLI", cliAction.getDisplayName());
  }

  @Test
  public void getUrlName() {
    assertEquals("cli", cliAction.getUrlName());
  }

  @Test
  public void isWebSocketSupported() {
    assertFalse(cliAction.isWebSocketSupported());
  }

  @Test
  public void testWebSocketNotSupported() throws Exception {
    try (MockedStatic<WebSockets> webSocketsMock = Mockito.mockStatic(WebSockets.class)) {
      StaplerRequest2 req = mock(StaplerRequest2.class);

      webSocketsMock.when(WebSockets::isSupported).thenReturn(false);

      HttpResponse response = cliAction.doWs(req);

      assertNotNull("Response should not be null", response);

      StaplerResponse2 resp = mock(StaplerResponse2.class);
      response.generateResponse(req, resp, null);

      verify(resp).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  @Test
  public void testInvalidOrigin() throws Exception {
    try (MockedStatic<WebSockets> webSocketsMock = Mockito.mockStatic(WebSockets.class);
        MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class)) {

      StaplerRequest2 req = mock(StaplerRequest2.class);
      StaplerResponse2 resp = mock(StaplerResponse2.class);

      webSocketsMock.when(WebSockets::isSupported).thenReturn(true);

      when(req.getHeader("Origin")).thenReturn("https://invalid-origin.com");

      Jenkins jenkins = mock(Jenkins.class);
      jenkinsMock.when(Jenkins::get).thenReturn(jenkins);
      when(jenkins.getRootUrlFromRequest()).thenReturn("https://correct-origin.com/");
      when(req.getContextPath()).thenReturn("");

      Field allowWebSocketField = CLIAction.class.getDeclaredField("ALLOW_WEBSOCKET");
      allowWebSocketField.setAccessible(true);
      allowWebSocketField.set(cliAction, null);

      HttpResponse response = cliAction.doWs(req);

      assertNotNull("Response should not be null", response);

      response.generateResponse(req, resp, null);

      verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
  }

  @Test
  public void testWebSocketSupportedAndAllowed() throws Exception {
    try (MockedStatic<WebSockets> webSocketsMock = Mockito.mockStatic(WebSockets.class);
        MockedStatic<Jenkins> jenkinsMock = Mockito.mockStatic(Jenkins.class)) {

      webSocketsMock.when(() -> WebSockets.upgrade(any(WebSocketSession.class)))
          .thenReturn(mock(HttpResponse.class));

      StaplerRequest2 req = mock(StaplerRequest2.class);

      webSocketsMock.when(WebSockets::isSupported).thenReturn(true);

      when(req.getHeader("Origin")).thenReturn("https://correct-origin.com");

      Jenkins jenkins = mock(Jenkins.class);
      jenkinsMock.when(Jenkins::get).thenReturn(jenkins);
      when(jenkins.getRootUrlFromRequest()).thenReturn("https://correct-origin.com/");
      when(req.getContextPath()).thenReturn("");

      Field allowWebSocketField = CLIAction.class.getDeclaredField("ALLOW_WEBSOCKET");
      allowWebSocketField.setAccessible(true);
      allowWebSocketField.set(cliAction, true);

      HttpResponse response = cliAction.doWs(req);

      assertNotNull("Response should not be null", response);
    }
  }

  @Test
  public void testWebSocketDisabled() throws Exception {
    try (MockedStatic<WebSockets> webSocketsMock = Mockito.mockStatic(WebSockets.class)) {
      StaplerRequest2 req = mock(StaplerRequest2.class);
      StaplerResponse2 resp = mock(StaplerResponse2.class);

      webSocketsMock.when(WebSockets::isSupported).thenReturn(true);

      Field allowWebSocketField = CLIAction.class.getDeclaredField("ALLOW_WEBSOCKET");
      allowWebSocketField.setAccessible(true);
      allowWebSocketField.set(cliAction, false);

      HttpResponse response = cliAction.doWs(req);

      assertNotNull("Response should not be null", response);

      response.generateResponse(req, resp, null);

      verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
  }
}
