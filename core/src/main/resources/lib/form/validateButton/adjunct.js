Behaviour.specify(
  ".Behaviour-validateButton",
  "Behaviour-validateButton",
  0,
  function (element) {
    element.onclick = function () {
      safeValidateButton(this);
    };
  },
);
