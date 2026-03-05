Behaviour.specify(
  ".app-adminmonitor-dismiss-button",
  "admin-monitor",
  0,
  function (element) {
    element.addEventListener("click", function (event) {
      event.preventDefault();
      const url = element.dataset.url;
      fetch(url, {
        method: "POST",
        cache: "no-cache",
        headers: crumb.wrap({
          "Content-Type": "application/x-www-form-urlencoded",
        }),
        body: new URLSearchParams({}),
      })
        .then((response) => {
          if (response.ok) {
            const parent = element.closest(".app-adminmonitor");
            if (parent) {
              parent.remove();
            }
          } else {
            console.warn(
              `AdminMonitor dismiss request failed with status ${response.status}`,
            );
          }
        })
        .catch((error) =>
          console.warn(`AdminMonitor dismiss request failed: ${error}`),
        );
    });
  },
);
