/*
 * The MIT License
 *
 * Copyright (c) 2026, Aitor Lopez
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

/*
 * The actions of the API token maximum lifetime page: Revoke and Shorten.
 *
 * The token owner's ID, the full name and the name of a token are chosen by users, so they are only ever used as text: they
 * go into the message of a dialog, which the dialog writes as text, or into the body of a request, and they are never
 * written as HTML. The titles and the labels of the buttons come from the server as static texts in data-* attributes,
 * because the dialog writes the labels of its buttons as HTML.
 */
(function () {
  /**
   * Fills the %name% placeholders of a static text with values. The replacement is a function, so what a value contains
   * ($&, $`, %other%) is taken as it is: a string would have its $ patterns interpreted. A placeholder that has no value stays.
   */
  function fillTemplate(template, values) {
    return template.replace(/%(\w+)%/g, function (placeholder, key) {
      return Object.prototype.hasOwnProperty.call(values, key)
        ? values[key]
        : placeholder;
    });
  }

  /**
   * The names that the row lists in data-shared-with, a JSON array of strings, which is what the server renders. A value
   * that is missing, or is not an array, lists no one, and the entries that are not strings are skipped.
   */
  function sharedNamesOf(row) {
    var names;
    try {
      names = JSON.parse(row.dataset.sharedWith);
    } catch {
      return [];
    }
    if (!Array.isArray(names)) {
      return [];
    }
    return names.filter(function (name) {
      return typeof name === "string";
    });
  }

  /**
   * The message of the confirmation. Revoking a token whose secret is shared revokes the others that have not expired too,
   * so they are listed, one per line, in a static text that has a %names% placeholder.
   */
  function confirmMessageOf(button, row) {
    var template = button.dataset.confirmMessageShared;
    if (
      button.dataset.tokenAction !== "revoke" ||
      row.dataset.sharesSecret !== "true" ||
      !template
    ) {
      return button.dataset.confirmMessage;
    }
    var names = sharedNamesOf(row).map(function (name) {
      return "- " + name;
    });
    return fillTemplate(template, { names: names.join("\n") });
  }

  function showFailure(button) {
    button.disabled = false;
    dialog.alert(button.dataset.errorTitle || "", {
      message: button.dataset.errorMessage,
    });
  }

  /**
   * Sends the action. The page is reloaded only when the server did it. When it refused, which is when the token changed
   * since the page was loaded, nothing was done, and the page says so and keeps what it shows.
   */
  function send(button, url, userId, uuid) {
    button.disabled = true;
    fetch(url, {
      method: "post",
      headers: crumb.wrap({ "Content-Type": "application/json" }),
      body: JSON.stringify({ userId: userId, uuid: uuid }),
    }).then(
      function (rsp) {
        if (rsp.ok) {
          window.location.reload();
        } else {
          showFailure(button);
        }
      },
      function () {
        showFailure(button);
      },
    );
  }

  function onActionClick(button) {
    var url = button.dataset.url;
    // the row says which token it is: found from the button, never by a selector that holds a user ID
    var row = button.closest("tr");
    if (!url || row === null) {
      return;
    }
    var userId = row.dataset.userId;
    var uuid = row.dataset.uuid;
    if (!userId || !uuid) {
      return;
    }

    // the labels are only passed when they are there, as a missing one would replace the default of the dialog
    var options = {
      message: confirmMessageOf(button, row),
      type: "destructive",
    };
    if (button.dataset.okText) {
      options.okText = button.dataset.okText;
    }
    if (button.dataset.cancelText) {
      options.cancelText = button.dataset.cancelText;
    }

    dialog.confirm(button.dataset.confirmTitle || "", options).then(
      function () {
        send(button, url, userId, uuid);
      },
      function () {},
    );
  }

  Behaviour.specify(
    ".token-lifetime-action",
    "api-token-lifetime-action",
    0,
    function (button) {
      button.addEventListener("click", function () {
        onActionClick(button);
      });
    },
  );
})();
