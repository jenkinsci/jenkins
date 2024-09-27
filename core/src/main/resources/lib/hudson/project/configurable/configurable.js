(function () {
  /* The JS formatting rules enforced in this repo can result in function declarations incompatible with HTMLUnit.
     As a workaround, split function definition and assignment. */
  function foo(el, ev) {
    let parameterized = el.dataset.parameterized;
    let success = el.dataset.buildSuccess;
    if (parameterized === "false") {
      fetch(el.href, {
        method: "post",
        headers: crumb.wrap({}),
      });
      hoverNotification(success, ev.target.parentNode);
      ev.preventDefault();
    }
  }
  window.lib_hudson_project_configurable_build_now_callback = foo;
})();
