import behaviorShim from "@/util/behavior-shim";

/**
 * Attaches click behavior to "Build Now" buttons to trigger a POST
 * request and show a success or error notification.
 */
behaviorShim.specify(
  "button[data-type='build-now']",
  "button-build-now",
  999,
  (button) => {
    button.addEventListener("click", function (event) {
      let success = button.dataset.buildSuccess;
      let failure = button.dataset.buildFailure;

      fetch(button.dataset.baseUrl + button.dataset.href, {
        method: "post",
        headers: crumb.wrap({}),
      }).then((rsp) => {
        if (rsp.status === 201) {
          notificationBar.show(success, notificationBar.SUCCESS);
        } else {
          notificationBar.show(failure, notificationBar.ERROR);
        }
      });
      event.preventDefault();
    });
  },
);
