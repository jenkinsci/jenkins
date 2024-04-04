const el = document.querySelector("#button-build");
if (el && !el.href) {
  el.addEventListener("click", () => {
    fetch("build?delay=0sec", {
      method: "post",
      headers: crumb.wrap({}),
    });
    notificationBar.show("Build scheduled", notificationBar.SUCCESS);
  });
}
