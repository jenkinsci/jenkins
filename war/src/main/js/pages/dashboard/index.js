import { showModal } from "@/components/modals";
import { createElementFromHtml } from "@/util/dom";

document.querySelector("#button-icon-legend").addEventListener("click", () => {
  const content = createElementFromHtml(
    "<div>" +
      document.querySelector("#template-icon-legend").innerHTML +
      "</div>"
  );

  showModal(content, {
    maxWidth: "550px",
  });
});
