window["lib_hudson_project_configurable_build_now_callback"] = function (
  el,
  ev,
) {
  let parameterized = el.dataset.parameterized;
  let success = el.dataset.success;
  if (parameterized === "false") {
    fetch(el.href, {
      method: "post",
      headers: crumb.wrap({}),
    });
    hoverNotification(success, ev.target.parentNode);
    ev.preventDefault();
  }
};
