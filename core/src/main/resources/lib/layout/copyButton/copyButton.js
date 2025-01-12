Behaviour.specify(
  ".jenkins-copy-button",
  "copyButton",
  0,
  function (copyButton) {
    if (isSecureContext) {
      copyButton.addEventListener("click", () => {
        var text = copyButton.getAttribute("text");
        if (copyButton.hasAttribute("ref")) {
          var ref = copyButton.getAttribute("ref");
          var target = document.getElementById(ref);
          if (target) {
            text = target.innerText;
          }
        }

        // Copy the text to the clipboard
        navigator.clipboard
          .writeText(text)
          .then(() => {
            copyButton.classList.add("jenkins-copy-button--copied");
            setTimeout(() => {
              copyButton.classList.remove("jenkins-copy-button--copied");
            }, 2000);
          })
          .catch(() => {
            hoverNotification(
              "Could not get permission to write to clipboard",
              copyButton,
            );
          });
      });
    } else {
      copyButton.disabled = true;
      copyButton.removeAttribute("tooltip");
      const parent = copyButton.parentElement;
      parent.setAttribute("tooltip", parent.dataset.messageInsecure);
    }
  },
);
