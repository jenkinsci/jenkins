import {
  initCapsLockIndicator,
  initPasswordVisibilityToggle,
} from "@/util/password-field";

const passwordField = document.querySelector("#j_password");
const toggleButton = document.querySelector("#togglePassword");
const capsLockWarning = document.querySelector("#capsLockWarning");

if (passwordField && toggleButton && capsLockWarning) {
  initPasswordVisibilityToggle(passwordField, toggleButton);
  initCapsLockIndicator(passwordField, capsLockWarning);
}
