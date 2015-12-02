package hudson.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class ResponseObjectTest {

  @Test
  public void emptyMessageReportsAsEmpty() throws Exception {
    // given
    ResponseObject response = new ResponseObject();

    // when
    boolean hasMessage = response.hasMessage();

    // then
    assertFalse(hasMessage);
  }

  @Test
  public void emptyWarningReportsAsEmpty() throws Exception {
    // given
    ResponseObject response = new ResponseObject();

    // when
    boolean hasWarning = response.hasWarning();

    // then
    assertFalse(hasWarning);
  }

  @Test
  public void nonEmptyMessageReportsAsNonEmpty() throws Exception {
    // given
    ResponseObject response = new ResponseObject("test", "");

    // when
    boolean hasMessage = response.hasMessage();

    // then
    assertTrue(hasMessage);
  }

  @Test
  public void nonEmptWarningReportsAsNonEmpty() throws Exception {
    // given
    ResponseObject response = new ResponseObject("", "test");

    // when
    boolean hasWarning = response.hasWarning();

    // then
    assertTrue(hasWarning);
  }

  @Test
  public void messageIsReturned() throws Exception {
    // given
    String expected = "test123";
    ResponseObject response = new ResponseObject(expected, "");

    // when
    String actual = response.getMessage();

    // then
    assertEquals(expected, actual);
  }

  @Test
  public void warningIsReturned() throws Exception {
    // given
    String expected = "test123";
    ResponseObject response = new ResponseObject("", expected);

    // when
    String actual = response.getWarning();

    // then
    assertEquals(expected, actual);
  }

  @Test
  public void emptyMessageIsUpdatedCorrectly() throws Exception {
    // given
    String expected = "test123";
    ResponseObject response = new ResponseObject();

    // when
    ResponseObject newResponse = response.withExtraMessage(expected);
    String actual = newResponse.getMessage();

    // then
    assertEquals(expected, actual);
  }

  @Test
  public void emptyWarningIsUpdatedCorrectly() throws Exception {
    // given
    String expected = "test123";
    ResponseObject response = new ResponseObject();

    // when
    ResponseObject newResponse = response.withExtraWarning(expected);
    String actual = newResponse.getWarning();

    // then
    assertEquals(expected, actual);
  }

  @Test
  public void nonEmptyMessageIsUpdatedCorrectly() throws Exception {
    // given
    String extra = "123";
    String initial = "test";
    ResponseObject response = new ResponseObject(initial, "");

    // when
    ResponseObject newResponse = response.withExtraMessage(extra);
    String actual = newResponse.getMessage();

    // then
    String expected = initial + "; " + extra;
    assertEquals(expected, actual);
  }

  @Test
  public void nonEmptyWarningIsUpdatedCorrectly() throws Exception {
    // given
    String extra = "123";
    String initial = "test";
    ResponseObject response = new ResponseObject("", initial);

    // when
    ResponseObject newResponse = response.withExtraWarning(extra);
    String actual = newResponse.getWarning();

    // then
    String expected = initial + "; " + extra;
    assertEquals(expected, actual);
  }

  @Test
  public void warningAndMessageAreSeparated() throws Exception {
    // given
    String warning = "warning";
    String message = "message";
    ResponseObject response = new ResponseObject(message, warning);

    // when
    String actual = response.getWarningAndMessge();

    // then
    String expected = warning + "; " + message;
    assertEquals(expected, actual);
  }

  @Test
  public void onlyWarningNotSeparated() throws Exception {
    // given
    String warning = "warning";
    ResponseObject response = new ResponseObject("", warning);

    // when
    String actual = response.getWarningAndMessge();

    // then
    assertEquals(warning, actual);
  }

  @Test
  public void onlyMessageNotSeparated() throws Exception {
    // given
    String message = "message";
    ResponseObject response = new ResponseObject(message, "");

    // when
    String actual = response.getWarningAndMessge();

    // then
    assertEquals(message, actual);
  }
}
