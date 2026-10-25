Behaviour.specify(
  "A.update-center-job-failure-show-details",
  "update-center-job-failure",
  0,
  function (anchor) {
    anchor.onclick = function (event) {
      event.preventDefault();
      // eslint-disable-next-line no-undef
      const n = findNext(this, function (el) {
        return el.tagName === "PRE";
      });
      n.style.display = "block";
      this.style.display = "none";
    };
  },
);
