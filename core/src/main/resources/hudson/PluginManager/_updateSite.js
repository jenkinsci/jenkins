document.getElementById("reset-to-default").onclick = function (event) {
  event.preventDefault();
  const siteUrlInput = document.getElementById("update-site-url");
  siteUrlInput.value = "https://updates.jenkins.io/current/update-center.json";
  siteUrlInput.dispatchEvent(new Event("change"));
};
