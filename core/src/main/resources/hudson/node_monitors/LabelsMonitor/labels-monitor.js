Behaviour.specify(".labelcolumnbox", 'LabelsMonitorColumn ', 0, function(e) {

  var dataAttribute = e.getAttribute("data");
  function toggle() {
    var extralabels = document.getElementById("labels-" + dataAttribute);
    var iconup = document.getElementById("icon-up-" + dataAttribute);
    var icondown = document.getElementById("icon-down-" + dataAttribute);
    if (extralabels.style.display === "block") {
      extralabels.style.display = "none";
      icondown.style.display = "block";
      iconup.style.display = "none";
    } else {
      extralabels.style.display = "block";
      icondown.style.display = "none";
      iconup.style.display = "block";
    };
  };
  e.onclick = toggle
});