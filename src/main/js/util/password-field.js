export function initPasswordVisibilityToggle(passwordField, toggleButton) {
  // Only shown when JavaScript is available, the toggle is inert without it
  toggleButton.hidden = false;

  toggleButton.addEventListener("click", () => {
    const showPassword = passwordField.type === "password";
    passwordField.type = showPassword ? "text" : "password";
    toggleButton.setAttribute("aria-pressed", String(showPassword));
  });
}

export function initCapsLockIndicator(passwordField, indicator) {
  // The Caps Lock state can only be read from input events, so track it
  // page-wide: both a click (mousedown) and tabbing (keydown) into the
  // field deliver the state before the field receives focus. A programmatic
  // focus (autofocus) cannot reveal the state until the first such event.
  let capsLockOn = false;

  function updateIndicator(event) {
    if (typeof event.getModifierState === "function") {
      capsLockOn = event.getModifierState("CapsLock");
    }
    indicator.hidden = !(
      capsLockOn && document.activeElement === passwordField
    );
  }

  document.addEventListener("keydown", updateIndicator);
  document.addEventListener("keyup", updateIndicator);
  document.addEventListener("mousedown", updateIndicator);
  passwordField.addEventListener("focus", () => {
    indicator.hidden = !capsLockOn;
  });
  passwordField.addEventListener("blur", () => {
    indicator.hidden = true;
  });
}
