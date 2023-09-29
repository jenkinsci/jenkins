Behaviour.specify(
  ".jenkins-copy-button",
  "copyButton",
  0,
  function (copyButton) {
    copyButton.addEventListener("click", () => {
      // HTMLUnit 2.70.0 does not recognize isSecureContext
      // https://issues.jenkins.io/browse/JENKINS-70895
      if (!window.isRunAsTest && isSecureContext) {
        // Make an invisible textarea element containing the text
        const fakeInput = document.createElement("textarea");
        fakeInput.value = copyButton.getAttribute("text");
        fakeInput.style.width = "1px";
        fakeInput.style.height = "1px";
        fakeInput.style.border = "none";
        fakeInput.style.padding = "0px";
        fakeInput.style.position = "absolute";
        fakeInput.style.top = "-99999px";
        fakeInput.style.left = "-99999px";
        fakeInput.setAttribute("tabindex", "-1");
        document.body.appendChild(fakeInput);

        // Select the text and copy it to the clipboard
        fakeInput.select();
        navigator.clipboard.writeText(fakeInput.value);

        // Remove the textarea element
        document.body.removeChild(fakeInput);

        // Show the completion message
        hoverNotification(copyButton.getAttribute("message"), copyButton);
      } else {
        hoverNotification(
          "Copy is only supported with a secure (HTTPS) connection",
          copyButton,
        );
      }
    });
  },
);
