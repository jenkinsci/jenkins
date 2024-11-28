Behaviour.specify("TEXTAREA.codemirror", "textarea", 0, function (e) {
  var config = e.getAttribute("codemirror-config");
  if (!config) {
    config = "";
  }
  try {
    config = JSON.parse("{" + config + "}");
  } catch (ex) {
    /*
     * Attempt to parse fairly common legacy format whose exact content is:
     * mode:'<MIME>'
     */
    let match = config.match("^mode: ?'([^']+)'$");
    if (match) {
      console.log(
        "Parsing simple legacy codemirror-config value using fallback: " +
          config,
      );
      config = { mode: match[1] };
    } else {
      console.log(
        "Failed to parse codemirror-config '{" + config + "}' as JSON",
        ex,
      );
      config = {};
    }
  }
  if (!config.onBlur) {
    config.onBlur = function (editor) {
      editor.save();
      editor.getTextArea().dispatchEvent(new Event("change"));
    };
  }
  var codemirror = CodeMirror.fromTextArea(e, config);
  e.codemirrorObject = codemirror;
  if (typeof codemirror.getScrollerElement !== "function") {
    // Maybe older versions of CodeMirror do not provide getScrollerElement method.
    codemirror.getScrollerElement = function () {
      return codemirror.getWrapperElement().querySelector(".CodeMirror-scroll");
    };
  }
  var lineCount = codemirror.lineCount();
  var lineHeight = codemirror.defaultTextHeight();

  var scroller = codemirror.getScrollerElement();
  scroller.setAttribute("style", "border:none;");
  scroller.style.height = Math.max(lineHeight * lineCount + 30, 130) + "px";

  // the form needs to be populated before the "Apply" button
  if (e.closest("form")) {
    // Protect against undefined element
    e.closest("form").addEventListener("jenkins:apply", function () {
      e.value = codemirror.getValue();
    });
  }
});

Behaviour.specify(
  "DIV.textarea-preview-container",
  "textarea",
  100,
  function (e) {
    var previewDiv = e.nextSibling;
    var showPreview = e.querySelector(".textarea-show-preview");
    var hidePreview = e.querySelector(".textarea-hide-preview");
    hidePreview.style.display = "none";
    previewDiv.style.display = "none";

    showPreview.onclick = function (event) {
      event.preventDefault();
      // Several TEXTAREAs may exist if CodeMirror is enabled. The first one has reference to the CodeMirror object.
      var textarea = e.parentNode.getElementsByTagName("TEXTAREA")[0];
      var text = "";
      //Textarea object will be null if the text area is disabled.
      if (textarea == null) {
        textarea = e.parentNode.getElementsByClassName("jenkins-readonly")[0];
        text = textarea != null ? textarea.innerText : "";
      } else {
        text = textarea.codemirrorObject
          ? textarea.codemirrorObject.getValue()
          : textarea.value;
      }
      var render = function (txt) {
        hidePreview.style.display = "";
        previewDiv.style.display = "";
        previewDiv.innerHTML = txt;
        layoutUpdateCallback.call();
      };

      fetch(rootURL + showPreview.getAttribute("previewEndpoint"), {
        method: "post",
        headers: crumb.wrap({
          "Content-Type": "application/x-www-form-urlencoded",
        }),
        body: new URLSearchParams({
          text: text,
        }),
      }).then((rsp) => {
        rsp.text().then((responseText) => {
          if (rsp.ok) {
            render(responseText);
          } else {
            render(rsp.status + " " + rsp.statusText + "<HR/>" + responseText);
          }
          return false;
        });
      });
    };

    hidePreview.onclick = function (event) {
      event.preventDefault();
      hidePreview.style.display = "none";
      previewDiv.style.display = "none";
    };
  },
);
