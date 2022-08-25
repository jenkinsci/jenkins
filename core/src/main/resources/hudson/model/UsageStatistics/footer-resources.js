Behaviour.addLoadEvent(function () {
  var targetDiv = document.querySelector("#usage-stats-div");
  var statData = targetDiv.getAttribute("data-inserted-from-java");
  loadScript("https://usage.jenkins.io/usage-stats.js?" + statData);
});
