Behaviour.specify(
  ".jenkins-copy-button",
  "copyButton",
  0,
  function (copyButton) {
    copyButton.addEventListener("click", () => {
      // HTMLUnit 2.70.0 does not recognize isSecureContext
      // https://issues.jenkins.io/browse/JENKINS-70895
      if (!window.isRunAsTest && isSecureContext) {
        // Copy the text to the clipboard
        navigator.clipboard
          .writeText(copyButton.getAttribute("text"))
          .then(() => {
            // Show the completion message
            hoverNotification(copyButton.getAttribute("message"), copyButton);
          })
          .catch(() => {
            hoverNotification(
              "Could not get permission to write to clipboard",
              copyButton,
            );
          });
      } else {
        hoverNotification(
          "Copy is only supported with a secure (HTTPS) connection",
          copyButton,
        );
      }
    });
  },
);
