window["lib_hudson_project_configurable_build_now_callback"] = function (
  el,
  ev
) {
  let parameterized = el.dataset.parameterized;
  let success = el.dataset.success;
  if (parameterized === "false") {
    new Ajax.Request(ev.target.href);
    hoverNotification(success, ev.target.parentNode);
    ev.preventDefault();
  }
};
