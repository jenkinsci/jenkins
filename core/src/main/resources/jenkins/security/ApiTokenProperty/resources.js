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
  function (element) {
    element.addEventListener("click", function (event) {
      event.preventDefault();
      revokeToken(element);
    });
  },
);

Behaviour.specify(
  ".api-token-property-token-save",
  "api-token-property-token-save",
  0,
  function (element) {
    element.addEventListener("click", function () {
      saveApiToken(element);
    });
  },
);

function revokeToken(anchorRevoke) {
  const repeatedChunk = anchorRevoke.closest(".repeated-chunk");
  const tokenList = repeatedChunk.closest(".token-list");
  const confirmMessage = anchorRevoke.getAttribute("data-confirm");
  const confirmTitle = anchorRevoke.getAttribute("data-confirm-title");
  const targetUrl = anchorRevoke.getAttribute("data-target-url");

  const inputUuid = repeatedChunk.querySelector("input.token-uuid-input");
  const tokenUuid = inputUuid.value;

  dialog
    .confirm(confirmTitle, { message: confirmMessage, type: "destructive" })
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
            if (repeatedChunk.querySelectorAll(".legacy-token").length > 0) {
              // we are revoking the legacy token
              const messageIfLegacyRevoked = anchorRevoke.getAttribute(
                "data-message-if-legacy-revoked",
              );

              const legacyInput = document.getElementById("apiToken");
              legacyInput.value = messageIfLegacyRevoked;
            }
            repeatedChunk.remove();
            adjustTokenEmptyListMessage(tokenList);
          }
        });
      },
      () => {},
    );
}

function saveApiToken(button) {
  if (button.classList.contains("request-pending")) {
    // avoid multiple requests to be sent if user is clicking multiple times
    return;
  }
  button.classList.add("request-pending");
  const targetUrl = button.getAttribute("data-target-url");
  const repeatedChunk = button.closest(".repeated-chunk ");
  const tokenList = repeatedChunk.closest(".token-list");
  const nameInput = repeatedChunk.querySelector('[name="tokenName"]');
  const tokenName = nameInput.value;

  fetch(targetUrl, {
    body: new URLSearchParams({ newTokenName: tokenName }),
    method: "post",
    headers: crumb.wrap({
      "Content-Type": "application/x-www-form-urlencoded",
    }),
  }).then((rsp) => {
    if (rsp.ok) {
      rsp.json().then((json) => {
        const errorSpan = repeatedChunk.querySelector(".error");
        if (json.status === "error") {
          errorSpan.innerHTML = json.message;
          errorSpan.classList.remove("jenkins-hidden");

          button.classList.remove("request-pending");
        } else {
          errorSpan.classList.remove("visible");

          const tokenName = json.data.tokenName;
          // in case the name was empty, the application will propose a default one
          nameInput.value = tokenName;

          const tokenValue = json.data.tokenValue;
          const tokenValueSpan =
            repeatedChunk.querySelector(".new-token-value");
          tokenValueSpan.innerText = tokenValue;
          tokenValueSpan.classList.remove("jenkins-hidden");

          // show the copy button
          const tokenCopyButton = repeatedChunk.querySelector(
            ".jenkins-copy-button",
          );
          tokenCopyButton.setAttribute("text", tokenValue);
          tokenCopyButton.classList.remove("jenkins-hidden");

          const tokenUuid = json.data.tokenUuid;
          const uuidInput = repeatedChunk.querySelector('[name="tokenUuid"]');
          uuidInput.value = tokenUuid;

          const warningMessage = repeatedChunk.querySelector(
            ".display-after-generation",
          );
          warningMessage.classList.remove("jenkins-hidden");

          // we do not want to allow user to create twice a token using same name by mistake
          button.remove();

          const revokeButton = repeatedChunk.querySelector(
            ".api-token-property-token-revoke",
          );
          revokeButton.classList.remove("jenkins-hidden");

          const cancelButton =
            repeatedChunk.querySelector(".repeatable-delete");
          cancelButton.remove();

          repeatedChunk.classList.add("token-list-fresh-item");
          const item = repeatedChunk.querySelector(".token-list-new-item");
          item.classList.remove("token-list-new-item");
          item.classList.add("token-list-existing-item");

          adjustTokenEmptyListMessage(tokenList);
        }
      });
    }
  });
}

function adjustTokenEmptyListMessage(tokenList) {
  const emptyListMessage = tokenList.querySelector(".token-list-empty-item");

  // number of token that are already existing or freshly created
  const numOfToken = tokenList.querySelectorAll(
    ".token-list-existing-item, .token-list-fresh-item",
  ).length;
  if (numOfToken >= 1) {
    if (!emptyListMessage.classList.contains("jenkins-hidden")) {
      emptyListMessage.classList.add("jenkins-hidden");
    }
  } else {
    if (emptyListMessage.classList.contains("jenkins-hidden")) {
      emptyListMessage.classList.remove("jenkins-hidden");
    }
  }
}
