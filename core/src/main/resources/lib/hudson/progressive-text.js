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
    let maxChunksAttr = holder.getAttribute("data-max-chunks");
    let maxChunks =
      maxChunksAttr !== null && maxChunksAttr !== ""
        ? Number(maxChunksAttr)
        : 1000;
    let showEarlierText =
      holder.getAttribute("data-show-earlier-text") || "Load earlier output";
    let hiddenChunksMessage =
      holder.getAttribute("data-hidden-chunks-message") ||
      "Earlier output hidden to prevent browser lag ({0} chunks).";

    var scroller = new AutoScroller(
      holder.closest(".progressive-text-container") || document.body,
    );

    const activeChunks = [];
    const prunedChunks = [];
    let banner = null;

    function updateBanner(e) {
      if (prunedChunks.length === 0) {
        if (banner) {
          banner.style.display = "none";
        }
        return;
      }
      if (!banner) {
        banner = document.createElement("button");
        banner.type = "button";
        banner.className =
          "jenkins-button jenkins-!-accent-color jenkins-!-padding-2 jenkins-!-margin-bottom-2 progressive-text-expand-button";
        banner.style.width = "100%";
        banner.style.justifyContent = "start";
        banner.addEventListener("click", () => {
          restoreEarlierChunks(e);
        });
        if (e.parentNode) {
          e.parentNode.insertBefore(banner, e);
        }
      }
      banner.style.display = "";
      const count = prunedChunks.length;
      banner.textContent = `${hiddenChunksMessage.replace("{0}", count)} ${showEarlierText}`;
    }

    function restoreEarlierChunks(e) {
      if (prunedChunks.length === 0) {
        return;
      }
      const frag = document.createDocumentFragment();
      const restored = [];
      while (prunedChunks.length > 0) {
        const chunk = prunedChunks.shift();
        frag.appendChild(chunk);
        restored.push(chunk);
      }
      activeChunks.unshift(...restored);

      const scrollContainer =
        holder.closest(".progressive-text-container") ||
        document.scrollingElement ||
        document.documentElement;
      const prevScrollHeight = scrollContainer
        ? scrollContainer.scrollHeight
        : 0;
      const prevScrollTop = scrollContainer ? scrollContainer.scrollTop : 0;

      e.insertBefore(frag, e.firstChild);

      if (scrollContainer && prevScrollTop > 0) {
        const heightDiff = scrollContainer.scrollHeight - prevScrollHeight;
        if (heightDiff > 0) {
          scrollContainer.scrollTop = prevScrollTop + heightDiff;
        }
      }

      updateBanner(e);
    }

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
          if (consoleAnnotator !== undefined && consoleAnnotator !== null) {
            e.consoleAnnotator = consoleAnnotator;
          }
          if (text !== "") {
            var p = document.createElement("DIV");
            e.appendChild(p); // Needs to be first for IE
            p.innerHTML = text;
            Behaviour.applySubtree(p);
            activeChunks.push(p);

            if (maxChunks > 0 && stickToBottom) {
              while (activeChunks.length > maxChunks) {
                const oldest = activeChunks.shift();
                if (oldest.parentNode === e) {
                  e.removeChild(oldest);
                }
                prunedChunks.push(oldest);
              }
              updateBanner(e);
            }

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
    const targetElement = document.getElementById(idref);
    if (targetElement) {
      targetElement.fetchedBytes = startOffset !== "" ? Number(startOffset) : 0;
      fetchNext(targetElement, href, onFinishEvent);
    }
  },
);
