Behaviour.specify("a.task-link-no-confirm", "task-link", 0, function (el) {
  if (el.onclick !== null) {
    return;
  }

  let post = el.dataset.taskPost;
  let callback = el.dataset.callback;
  let success = el.dataset.taskSuccess;
  let href = el.href;

  if (callback !== undefined) {
    el.onclick = function (ev) {
      window[callback](el, ev);
    };
    return;
  }

  if (post === "true") {
    el.onclick = function (ev) {
      fetch(href, {
        method: "post",
        headers: crumb.wrap({}),
      });
      hoverNotification(success, el.parentNode);
      ev.preventDefault();
    };
  }
});
