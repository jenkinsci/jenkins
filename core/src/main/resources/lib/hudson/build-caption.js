(function(){
  function updateBuildCaptionIcon(){
    new Ajax.Request("statusIcon",{
      method: "get",
      onComplete: function(rsp,_) {
        var isBuilding = rsp.getResponseHeader("X-Building");
        if (isBuilding == "true") {
          setTimeout(updateBuildCaptionIcon, 5000)
        } else {
          var progressBar = document.querySelector(".build-caption-progress-container");
          if (progressBar) {
            progressBar.style.display = "none";
          }
        }
        document.querySelector(".build-caption .icon-xlg").outerHTML = rsp.responseText;
      }
    });
  }

  window.addEventListener("load", function(){
    Event.observe(window, "jenkins:consoleFinished", updateBuildCaptionIcon);
  });

})();
