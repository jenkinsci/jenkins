Behaviour.specify(".labelcolumnbox", 'LabelsMonitorColumn ', 0, function(e) {

  function toggle() {
    var extralabels = document.getElementById("labels-" + e.getAttribute("data"));
    if (extralabels.style.display === "block") {
      extralabels.style.display = "none";
    } else {
      extralabels.style.display = "block";
    };
  };
  e.onclick = toggle
});