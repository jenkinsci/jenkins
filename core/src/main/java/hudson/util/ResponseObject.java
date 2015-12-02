package hudson.util;

/**
 * A class used to contain types of string response. Extra messages and warnings
 * can be appended to the class until a response is required.
 * The purpose of this class is to allow methods to update the message or
 * warning response without needing to return the entire response, allowing more
 * complex responses to be constructed.
 */
public class ResponseObject {
  private final String message;
  private final String warning;

  public ResponseObject() {
    message = "";
    warning = "";
  }

  public ResponseObject(String message, String warning) {
    this.message = message;
    this.warning = warning;
  }

  public boolean hasMessage() {
    return !message.isEmpty();
  }

  public boolean hasWarning() {
    return !warning.isEmpty();
  }

  public String getMessage() {
    return message;
  }

  public String getWarning() {
    return warning;
  }
  
  public String getWarningAndMessge() {
      if (hasWarning() && hasMessage()) {
          return warning + "; " + message;
      } else {
          return warning + message;
      }
  }

  public ResponseObject withExtraMessage(String message) {
    String updatedMessage;
    if (this.hasMessage()) {
      updatedMessage = this.message + "; " + message;
    } else {
      updatedMessage = message;
    }
    return new ResponseObject(updatedMessage, warning);
  }

  public ResponseObject withExtraWarning(String warning) {
    String updatedWarning;
    if (this.hasWarning()) {
      updatedWarning = this.warning + "; " + warning;
    } else {
      updatedWarning = warning;
    }
    return new ResponseObject(message, updatedWarning);
  }
}
