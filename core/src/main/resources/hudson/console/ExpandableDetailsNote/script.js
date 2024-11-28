(function () {
  Behaviour.specify(
    "BUTTON.reveal-expandable-detail",
    "ExpandableDetailsNote",
    0,
    function (e) {
      e.addEventListener("click", () => {
        const detail = e.nextSibling;
        detail.style.display =
          detail.style.display == "block" ? "none" : "block";
      });
    },
  );
})();
