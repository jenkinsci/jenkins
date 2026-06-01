Behaviour.specify(
  ".progressiveText-holder",
  "progressive-text",
  0,
  function (holder) {
    let href = holder.getAttribute("data-href");
    let idref = holder.getAttribute("data-idref");
    let spinner = holder.getAttribute("data-spinner");
    let startOffset = holder.getAttribute("data-start-offset");
    let onFinishEvent = holder.getAttribute("data-on-finish-event");
    let errorMessage = holder.getAttribute("data-error-message");

    var scroller = new AutoScroller(
      holder.closest(".progressive-text-container") || document.body,
    );
    /*
  fetches the latest update from the server
  @param e
      DOM node that gets the text appended to
  @param href
      Where to retrieve additional text from
  */
    function fetchNext(e, href, onFinishEvent) {
      var headers = crumb.wrap({
        "Content-Type": "application/x-www-form-urlencoded",
        Accept: "multipart/form-data, */*",
      });
      if (e.consoleAnnotator !== undefined) {
        headers["X-ConsoleAnnotator"] = e.consoleAnnotator;
      }

      fetch(href, {
        method: "post",
        headers,
        body: new URLSearchParams({
          start: e.fetchedBytes,
        }),
      }).then((rsp) => {
        if (rsp.status >= 500 || rsp.status === 0) {
          setTimeout(function () {
            fetchNext(e, href, onFinishEvent);
          }, 1000);
          return;
        }
        if (rsp.status === 403) {
          // likely an expired crumb
          location.reload();
          return;
        }
        var stickToBottom = scroller.isSticking();
        if (rsp.status >= 400) {
          var p = document.createElement("DIV");
          e.appendChild(p);
          p.innerHTML = '<br/><div class="error">' + errorMessage + "</div>";
          if (stickToBottom) {
            scroller.scrollToBottom();
          }
          if (spinner !== "") {
            document.getElementById(spinner).style.display = "none";
          }
          return;
        }
        let parse;
        if (
          rsp.headers.get("Content-Type")?.startsWith("multipart/form-data")
        ) {
          parse = rsp.formData().then((data) => {
            const text = data.get("text");
            const meta = JSON.parse(data.get("meta"));
            return { text, ...meta };
          });
        } else {
          parse = rsp.text().then((text) => {
            return {
              text,
              end: rsp.headers.get("X-Text-Size"),
              consoleAnnotator: rsp.headers.get("X-ConsoleAnnotator"),
              completed: rsp.headers.get("X-More-Data") !== "true",
            };
          });
        }
        /* append text and do autoscroll if applicable */
        parse.then(({ text, end, consoleAnnotator, completed }) => {
          e.fetchedBytes = end;
          e.consoleAnnotator = consoleAnnotator;
          if (text !== "") {
            var p = document.createElement("DIV");
            e.appendChild(p); // Needs to be first for IE
            p.innerHTML = text;
            Behaviour.applySubtree(p);
            if (stickToBottom) {
              scroller.scrollToBottom();
            }
          }
          if (!completed) {
            setTimeout(function () {
              fetchNext(e, href, onFinishEvent);
            }, 1000);
          } else {
            if (spinner !== "") {
              document.getElementById(spinner).style.display = "none";
            }
            if (onFinishEvent) {
              window.dispatchEvent(new Event(onFinishEvent));
            }
          }
        });
      });
    }
    document.getElementById(idref).fetchedBytes =
      startOffset !== "" ? Number(startOffset) : 0;
    fetchNext(document.getElementById(idref), href, onFinishEvent);
  },
);
