import behaviorShim from "@/util/behavior-shim";

behaviorShim.specify(
  "button[data-button-type='build']",
  "TODO",
  100,
  function (e) {
    e.addEventListener("click", () => {
      fetch(e.dataset.projectId + "build?delay=0sec", {
        method: "post",
        headers: crumb.wrap({}),
      });
      notificationBar.show(e.dataset.buildScheduled, notificationBar.SUCCESS);
    });
  },
);
