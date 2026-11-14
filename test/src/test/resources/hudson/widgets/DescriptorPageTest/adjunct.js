window.descriptorPageTestAdjunctRuns = (window.descriptorPageTestAdjunctRuns || 0) + 1;
Behaviour.specify(".descriptor-page-test", "descriptor-page-test", 0, function (e) {
  e.textContent = "ready";
});
