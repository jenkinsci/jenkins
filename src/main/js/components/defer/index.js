import behaviorShim from "@/util/behavior-shim";

function init() {
  behaviorShim.specify(".defer-element", "-defer-", 1000, (element) => {
    const placeholder = element.previousElementSibling;
    renderOnDemand(element, () => {
      placeholder.remove();
    });
  });
}

export default { init };
