import { getI18n } from "@/util/i18n";

const passwordField = document.querySelector("#password1");
const password2Field = document.querySelector("#password2");
const showPasswordField = document.querySelector("#showPassword");
const passwordStrengthWrapper = document.querySelector(
  "#passwordStrengthWrapper",
);
const passwordStrengthIndicator = document.querySelector("#passwordStrength");

updatePasswordStrength();

passwordField.addEventListener("input", updatePasswordStrength);

function updatePasswordStrength() {
  if (passwordField.value.length === 0) {
    passwordStrengthWrapper.hidden = true;
    return;
  }

  passwordStrengthWrapper.hidden = false;
  const score = passwordScore(passwordField.value);
  passwordStrengthIndicator.innerText = passwordStrength(score);
  passwordStrengthIndicator.style.color = passwordStrengthColor(score);
  password2Field.value = passwordField.value;
}

// Toggle password visibility
showPasswordField.addEventListener("change", () => {
  if (showPasswordField.checked) {
    passwordField.type = "text";
  } else {
    passwordField.type = "password";
  }
});

function passwordScore(password) {
  let score = 0;

  if (!password) {
    return score;
  }

  // Award every unique letter until 5 repetitions
  const letters = {};

  for (let i = 0; i < password.length; i++) {
    letters[password[i]] = (letters[password[i]] || 0) + 1;
    score += 5.0 / letters[password[i]];
  }

  // Bonus points for mixing it up
  const variations = {
    digits: /\d/.test(password),
    lower: /[a-z]/.test(password),
    upper: /[A-Z]/.test(password),
    nonWords: /\W/.test(password),
  };

  let variationCount = 0;
  for (const check in variations) {
    variationCount += variations[check] === true ? 1 : 0;
  }
  score += (variationCount - 1) * 10;

  return score;
}

function passwordStrength(score) {
  if (score > 80) {
    return getI18n("strength-strong");
  }
  if (score > 60) {
    return getI18n("strength-moderate");
  }
  if (score >= 30) {
    return getI18n("strength-weak");
  }
  return getI18n("strength-poor");
}

function passwordStrengthColor(score) {
  if (score > 80) {
    return "var(--green)";
  }
  if (score > 60) {
    return "var(--yellow)";
  }
  if (score >= 30) {
    return "var(--orange)";
  }
  return "var(--error-color)";
}
