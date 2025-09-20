import { createElementFromHtml } from "@/util/dom";

document
  .querySelector("#button-computer-icon-legend")
  .addEventListener("click", () => {
    const template = document.querySelector("#template-computer-icon-legend");
    const title = template.getAttribute("data-title");
    const content = createElementFromHtml(
      "<div>" + template.innerHTML + "</div>",
    );

    dialog.modal(content, {
      maxWidth: "550px",
      title: title,
    });
  });
