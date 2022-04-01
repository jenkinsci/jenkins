const toggleSwitches = document.querySelectorAll(".jenkins-toggle-switch__input")

toggleSwitches.forEach((toggleSwitch) => {
  toggleSwitch.addEventListener("change", function () {
    const label = toggleSwitch.nextSibling
    const enabledLabel = label.querySelector(".jenkins-toggle-switch__label__enabled-title")

    // We don't need to do anything for toggle switches that don't have checkedTitle set
    if (enabledLabel == null) {
      return;
    }

    const disabledLabel = label.querySelector(".jenkins-toggle-switch__label__disabled-title")

    function animateLabels(labelOne, labelTwo) {
      labelOne.style.opacity = 1;
      labelOne.style.transform = "translateY(0) rotateX(0deg)";
      labelTwo.style.opacity = 0;
      labelTwo.style.transform = "translateY(-1.5rem) rotateX(-75deg)";

      setTimeout(function() {
        labelTwo.style.transform = "translateY(1.5rem) rotateX(75deg)";
      }, 550);
    }

    if (toggleSwitch.checked) {
      animateLabels(enabledLabel, disabledLabel)
    } else {
      animateLabels(disabledLabel, enabledLabel)
    }
  })

  toggleSwitch.dispatchEvent(new Event("change"));
})
