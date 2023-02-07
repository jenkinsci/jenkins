Behaviour.specify("a.task-link-no-confirm", "task-link", 0, function (el) {
  if (el.onclick !== null) {
    return;
  }

  let post = el.dataset.post;
  let callbackElementId = el.dataset.callback;
  let success = el.dataset.success;
  let href = el.href;

  if (callbackElementId !== undefined) {
    el.onclick = function (ev) {
      let callbackElement = document.getElementById(callbackElementId);
      let callback = callbackElement.dataset.callback;
      window[callback](callbackElement, ev);
    };
    return;
  }

  if (post === "true") {
    el.onclick = function (ev) {
      new Ajax.Request(href);
      hoverNotification(success, el.parentNode);
      ev.preventDefault();
    };
  }
});
