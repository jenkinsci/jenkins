import behaviorShim from "@/util/behavior-shim";

function init() {
  behaviorShim.specify(".defer-element", "-defer-", 1000, (element) => {
    const parent = element.parentElement;
    const placeholder = element.previousElementSibling;

    renderOnDemand(element, () => {
      placeholder.remove();

      // Emit DOMContentLoaded in case it's tracked in a script
      const evt = new Event("DOMContentLoaded", { bubbles: true });
      parent.dispatchEvent(evt);
    });
  });
}

export default { init };
