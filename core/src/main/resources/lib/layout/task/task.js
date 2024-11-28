Behaviour.specify("a.task-link-no-confirm", "task-link", 0, function (el) {
  if (el.onclick !== null) {
    return;
  }

  let post = el.dataset.taskPost;
  let callback = el.dataset.callback;
  let success = el.dataset.taskSuccess;
  let failure = el.dataset.taskFailure;
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
      }).then((rsp) => {
        if (rsp.ok) {
          notificationBar(success, notificationBar.SUCCESS);
        } else {
          notificationBar(failure, notificationBar.ERROR);
        }
      });
      ev.preventDefault();
    };
  }
});
