(function () {
  showResetToDefaultButtonOrNot();
})();

function showResetToDefaultButtonOrNot() {
  const resetButton = document.getElementById("reset-to-default");
  const siteUrlInput = document.getElementById("update-site-url");
  if (siteUrlInput.value === "https://updates.jenkins.io/update-center.json") {
    resetButton.style.display = "none";
  } else {
    resetButton.style.display = "";
  }
}

document.getElementById("update-site-url").onchange =
  showResetToDefaultButtonOrNot;

document.getElementById("reset-to-default").onclick = function (event) {
  event.preventDefault();
  const siteUrlInput = document.getElementById("update-site-url");
  siteUrlInput.value = "https://updates.jenkins.io/update-center.json";
  siteUrlInput.dispatchEvent(new Event("change"));
};
