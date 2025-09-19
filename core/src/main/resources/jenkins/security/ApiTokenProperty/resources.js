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
Behaviour.specify(
  ".api-token-property-token-revoke",
  "api-token-property-token-revoke",
  0,
  function (button) {
    button.onclick = function () {
      revokeToken(button);
    };
  },
);

function revokeToken(anchorRevoke) {
  const tokenRow = anchorRevoke.closest(".token-card");
  const confirmMessage = anchorRevoke.getAttribute("data-confirm");
  const confirmTitle = anchorRevoke.getAttribute("data-confirm-title");
  const targetUrl = anchorRevoke.getAttribute("data-target-url");
  const tokenUuid = tokenRow.id;

  dialog
    .confirm(confirmTitle, {
      message: confirmMessage,
      type: "destructive",
      okText: anchorRevoke.dataset.buttonText,
    })
    .then(
      () => {
        fetch(targetUrl, {
          body: new URLSearchParams({ tokenUuid: tokenUuid }),
          method: "post",
          headers: crumb.wrap({
            "Content-Type": "application/x-www-form-urlencoded",
          }),
        }).then((rsp) => {
          if (rsp.ok) {
            if (tokenRow.classList.contains("legacy-token")) {
              // we are revoking the legacy token
              const messageIfLegacyRevoked = anchorRevoke.getAttribute(
                "data-message-if-legacy-revoked",
              );

              const legacyInput = document.getElementById("apiToken");
              legacyInput.value = messageIfLegacyRevoked;
            }
            tokenRow.remove();
            adjustTokenEmptyListMessage();
          }
        });
      },
      () => {},
    );
}

Behaviour.specify(
  ".api-token-property-add",
  "api-token-property-add",
  0,
  function (button) {
    button.onclick = function () {
      addToken(button);
    };
  },
);

function appendTokenToTable(data) {
  const rowTemplate = document.getElementById("api-token-row-template");
  const apiTokenRow = rowTemplate.firstElementChild.cloneNode(true);
  const tokenList = document.getElementById("api-token-list");
  const tokenShowButton = apiTokenRow.querySelector(
    ".api-token-property-token-show",
  );
  apiTokenRow.id = data.tokenUuid;
  apiTokenRow.querySelector(".token-name").innerText = data.tokenName;
  tokenShowButton.dataset.tokenValue = data.tokenValue;
  tokenShowButton.dataset.title = data.tokenName;
  tokenShowButton.classList.remove("jenkins-hidden");
  tokenList.appendChild(apiTokenRow);
  adjustTokenEmptyListMessage();
  Behaviour.applySubtree(apiTokenRow);
}

function addToken(button) {
  const targetUrl = button.dataset.targetUrl;
  const promptMessage = button.dataset.promptMessage;
  const promptName = button.dataset.messageName;
  const expirationMessage = button.dataset.messageExpiration;
  const generateMessage = button.dataset.generate;
  const cancelMessage = button.dataset.cancel;
  const doneMessage = button.dataset.buttonDone;

  dialog
    .prompt(promptMessage, {
      message: promptName,
      okText: generateMessage,
      cancelText: cancelMessage,
      maxWidth: "400px",
      minWidth: "400px",
    })
    .then(
      (tokenName) => {
        const content = document.createElement("div");
        const nameAndExpiration = document.createElement("div");
        nameAndExpiration.className = "name-and-expiration";
        content.appendChild(nameAndExpiration);

        const nameBlock = document.createElement("div");
        nameBlock.className = "name-block";
        nameAndExpiration.appendChild(nameBlock);

        const nameLabel = document.createElement("label");
        nameLabel.textContent = promptName;
        nameBlock.appendChild(nameLabel);

        const nameInput = document.createElement("input");
        nameInput.className = "token-name-input";
        nameInput.type = "text";
        nameBlock.appendChild(nameInput);

        const expirationBlock = document.createElement("div");
        expirationBlock.className = "expiration-block";
        nameAndExpiration.appendChild(expirationBlock);

        const expirationLabel = document.createElement("label");
        expirationLabel.textContent = expirationMessage;
        expirationBlock.appendChild(expirationLabel);

        const expirationSelect = document.createElement("select");
        expirationSelect.className = "token-expiration-select";
        expirationBlock.appendChild(expirationSelect);

        const noExpirationOption = document.createElement("option");
        noExpirationOption.value = "no-expiration";
        noExpirationOption.textContent = "No expiration";
        expirationSelect.appendChild(noExpirationOption);

        const thirtyDaysOption = document.createElement("option");
        thirtyDaysOption.value = "30-days";
        thirtyDaysOption.textContent = "30 days";
        expirationSelect.appendChild(thirtyDaysOption);

        const ninetyDaysOption = document.createElement("option");
        ninetyDaysOption.value = "90-days";
        ninetyDaysOption.textContent = "90 days";
        expirationSelect.appendChild(ninetyDaysOption);

        const oneYearOption = document.createElement("option");
        oneYearOption.value = "1-year";
        oneYearOption.textContent = "1 year";
        expirationSelect.appendChild(oneYearOption);

        const customOption = document.createElement("option");
        customOption.value = "custom";
        customOption.textContent = "Custom";
        expirationSelect.appendChild(customOption);

        const customDateBlock = document.createElement("div");
        customDateBlock.className = "custom-date-block";
        customDateBlock.style.display = "none";
        expirationBlock.appendChild(customDateBlock);

        const customDateInput = document.createElement("input");
        customDateInput.type = "date";
        customDateBlock.appendChild(customDateInput);

        expirationSelect.addEventListener("change", () => {
          if (expirationSelect.value === "custom") {
            customDateBlock.style.display = "block";
          } else {
            customDateBlock.style.display = "none";
          }
        });

        dialog.confirm(prompt, {
          content: content,
          okText: generateMessage,
          cancelText: cancelMessage,
          maxWidth: "400px",
          minWidth: "400px",
        }).then(
          () => {
            const tokenName = nameInput.value;
            let expiration = expirationSelect.value;
            if (expiration === "custom") {
              expiration = customDateInput.value;
            }
            fetch(targetUrl + "?newTokenName=" + tokenName + "&expiration=" + expiration, {
              method: "POST",
              headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                [crumb.headerName]: crumb.value,
              },
            }).then((rsp) => {
              if (rsp.ok) {
                rsp.json().then((json) => {
                  if (json.status === "error") {
                    dialog.alert(json.message, {
                      type: "destructive",
                    });
                  } else {
                    appendTokenToTable(json.data);
                    showToken(
                      json.data.tokenName,
                      json.data.tokenValue,
                      button.dataset.buttonDone,
                    );
                  }
                });
              }
            });
          },
          () => {},
        );
      },
      () => {},
    );
}

function showToken(tokenName, tokenValue, doneText) {
  const tokenTemplate = document.getElementById("api-token-template");
  const apiTokenMessage = tokenTemplate.firstElementChild.cloneNode(true);

  const tokenValueSpan = apiTokenMessage.querySelector(".api-token-new-value");
  tokenValueSpan.innerText = tokenValue;

  if (isSecureContext) {
    const tokenCopyButton = apiTokenMessage.querySelector(
      ".jenkins-copy-button",
    );
    tokenCopyButton.setAttribute("text", tokenValue);
    tokenCopyButton.classList.remove("jenkins-hidden");
  }
  Behaviour.applySubtree(apiTokenMessage);

  dialog.alert(tokenName, {
    content: apiTokenMessage,
    okText: doneText,
    maxWidth: "500px",
  });
}

Behaviour.specify(
  ".api-token-property-token-rename",
  "api-token-property-token-rename",
  0,
  function (button) {
    button.onclick = function () {
      renameToken(button);
    };
  },
);

function renameToken(button) {
  const targetUrl = button.dataset.targetUrl;
  const tokenRow = button.closest(".token-card");
  const promptValue = tokenRow.querySelector(".token-name").innerText;
  dialog
    .prompt(button.dataset.renameTitle, {
      message: button.dataset.renameMessage,
      okText: button.dataset.buttonText,
      promptValue: promptValue,
      maxWidth: "400px",
      minWidth: "400px",
    })
    .then(
      (newName) => {
        fetch(targetUrl, {
          body: new URLSearchParams({
            newName: newName,
            tokenUuid: tokenRow.id,
          }),
          method: "post",
          headers: crumb.wrap({
            "Content-Type": "application/x-www-form-urlencoded",
          }),
        }).then((rsp) => {
          if (rsp.ok) {
            rsp.json().then((json) => {
              if (json.status === "error") {
                dialog.alert(json.message, {
                  type: "destructive",
                });
              } else {
                const tokenField = tokenRow.querySelector(".token-name");
                const tokenShowButton = tokenRow.querySelector(
                  ".api-token-property-token-show",
                );
                tokenShowButton.dataset.title = newName;
                tokenField.innerText = newName;
              }
            });
          }
        });
      },
      () => {},
    );
}

function adjustTokenEmptyListMessage() {
  const tokenList = document.querySelector(".token-list");
  const emptyListMessage = tokenList.querySelector(".token-list-empty-item");
  const apiTokenList = document.getElementById("api-token-list");
  const apiTokenContainer = document.getElementById("api-tokens");

  // number of token that are already existing or freshly created
  const numOfToken = apiTokenList.childElementCount;
  if (numOfToken >= 1) {
    emptyListMessage.classList.toggle("jenkins-hidden", true);
    apiTokenContainer.classList.toggle("jenkins-hidden", false);
  } else {
    emptyListMessage.classList.toggle("jenkins-hidden", false);
    apiTokenContainer.classList.toggle("jenkins-hidden", true);
  }
}

Behaviour.specify(
  ".api-token-property-token-show",
  "api-token-property-token-show",
  0,
  function (button) {
    button.onclick = function () {
      showToken(
        button.dataset.title,
        button.dataset.tokenValue,
        button.dataset.buttonDone,
      );
    };
  },
);

(function () {
  const extendButtons = document.querySelectorAll(".api-token-property-token-extend");
  extendButtons.forEach((extendButton) => {
      const targetUrl = extendButton.dataset.targetUrl;
      const tokenUuid = extendButton.dataset.tokenUuid;
      const prompt = extendButton.dataset.promptMessage;
      const expirationMessage = extendButton.dataset.messageExpiration;
      const extendMessage = extendButton.dataset.extend;
      const successTitle = extendButton.dataset.successTitle;
      const cancelMessage = extendButton.dataset.cancel;
      const doneMessage = extendButton.dataset.buttonDone;

      extendButton.addEventListener("click", () => {
          const content = document.createElement("div");
          const expirationBlock = document.createElement("div");
          expirationBlock.className = "expiration-block";
          content.appendChild(expirationBlock);

          const expirationLabel = document.createElement("label");
          expirationLabel.textContent = expirationMessage;
          expirationBlock.appendChild(expirationLabel);

          const expirationSelect = document.createElement("select");
          expirationSelect.className = "token-expiration-select";
          expirationBlock.appendChild(expirationSelect);

          const noExpirationOption = document.createElement("option");
          noExpirationOption.value = "no-expiration";
          noExpirationOption.textContent = "No expiration";
          expirationSelect.appendChild(noExpirationOption);

          const thirtyDaysOption = document.createElement("option");
          thirtyDaysOption.value = "30-days";
          thirtyDaysOption.textContent = "30 days";
          expirationSelect.appendChild(thirtyDaysOption);

          const ninetyDaysOption = document.createElement("option");
          ninetyDaysOption.value = "90-days";
          ninetyDaysOption.textContent = "90 days";
          expirationSelect.appendChild(ninetyDaysOption);

          const oneYearOption = document.createElement("option");
          oneYearOption.value = "1-year";
          oneYearOption.textContent = "1 year";
          expirationSelect.appendChild(oneYearOption);

          const customOption = document.createElement("option");
          customOption.value = "custom";
          customOption.textContent = "Custom";
          expirationSelect.appendChild(customOption);

          const customDateBlock = document.createElement("div");
          customDateBlock.className = "custom-date-block";
          customDateBlock.style.display = "none";
          expirationBlock.appendChild(customDateBlock);

          const customDateInput = document.createElement("input");
          customDateInput.type = "date";
          customDateBlock.appendChild(customDateInput);

          expirationSelect.addEventListener("change", () => {
              if (expirationSelect.value === "custom") {
                  customDateBlock.style.display = "block";
              } else {
                  customDateBlock.style.display = "none";
              }
          });

          dialog.confirm(prompt, {
              content: content,
              okText: extendMessage,
              cancelText: cancelMessage,
              onOk: () => {
                  let expiration = expirationSelect.value;
                  if (expiration === "custom") {
                      expiration = customDateInput.value;
                  }
                  fetch(targetUrl + "?tokenUuid=" + tokenUuid + "&expiration=" + expiration, {
                      method: "POST",
                      headers: {
                          "Content-Type": "application/x-www-form-urlencoded",
                          [crumb.headerName]: crumb.value,
                      },
                  }).then((rsp) => {
                      if (rsp.ok) {
                          rsp.json().then((json) => {
                              const tokenExpiration = document.querySelector("#" + tokenUuid + " .token-expiration");
                              tokenExpiration.textContent = json.tokenExpiration;
                              dialog.alert(successTitle, {
                                  okText: doneMessage,
                              });
                          });
                      } else {
                          rsp.text().then((text) => {
                              dialog.alert(text);
                          });
                      }
                  });
              },
})();