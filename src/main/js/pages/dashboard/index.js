import behaviorShim from "@/util/behavior-shim";
import { createElementFromHtml } from "@/util/dom";

behaviorShim.specify("#button-icon-legend", "icon-legend", 999, (button) => {
  button.addEventListener("click", () => {
    const template = document.querySelector("#template-icon-legend");
    const title = template.getAttribute("data-title");
    const content = createElementFromHtml(
      "<div>" + template.innerHTML + "</div>",
    );

    dialog.modal(content, {
      maxWidth: "550px",
      title: title,
    });
  });
});
