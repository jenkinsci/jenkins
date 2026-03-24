(function () {
  /* The JS formatting rules enforced in this repo can result in function declarations incompatible with HTMLUnit.
     As a workaround, split function definition and assignment. */
  function foo(el, ev) {
    let parameterized = el.dataset.parameterized;
    let success = el.dataset.buildSuccess;
    let failure = el.dataset.buildFailure;
    if (parameterized === "false") {
      fetch(el.href, {
        method: "post",
        headers: crumb.wrap({}),
      }).then((rsp) => {
        if (rsp.status === 201) {
          notificationBar.show(success, notificationBar.SUCCESS);
        } else {
          notificationBar.show(failure, notificationBar.ERROR);
        }
      });
      ev.preventDefault();
    }
  }
  window.lib_hudson_project_configurable_build_now_callback = foo;
})();
