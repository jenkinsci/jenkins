import { toId } from "@/util/dom";
import BehaviorShim from "@/util/behavior-shim";

/*
 * Generates a jump list for the active breadcrumb to jump to
 * sections on the page (if using <f:breadcrumb-config-outline />)
 */
function init() {
  const inpageNavigationBreadcrumb = document.querySelector("#inpage-nav div");

  if (inpageNavigationBreadcrumb) {
    BehaviorShim.specify("form", "inpage-nav", 999, (element) => {
      inpageNavigationBreadcrumb.items = Array.from(
        element.querySelectorAll(
          "& > div > .jenkins-section > .jenkins-section__title",
        ),
      ).map((section) => {
        section.id = toId(section.textContent);
        return { label: section.textContent, url: "#" + section.id };
      });
    });
  }
}

export default { init };
