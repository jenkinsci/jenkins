import behaviorShim from "@/util/behavior-shim";

function registerConfirmationLink(element) {
  const post = element.getAttribute("data-post") === "true";
  const href = element.getAttribute("data-url");
  const message = element.getAttribute("data-message");
  const title = element.getAttribute("data-title");
  const destructive = element.getAttribute("data-destructive");
  let type = "default";
  if (destructive === "true") {
    type = "destructive";
  }

  element.addEventListener("click", function (e) {
    e.preventDefault();
    dialog.confirm(title, { message: message, type: type }).then(
      () => {
        var form = document.createElement("form");
        form.setAttribute("method", post ? "POST" : "GET");
        form.setAttribute("action", href);
        if (post) {
          crumb.appendToForm(form);
        }
        document.body.appendChild(form);
        form.submit();
      },
      () => {},
    );
    return false;
  });
}

function init() {
  behaviorShim.specify(
    "A.confirmation-link",
    "confirmation-link",
    0,
    (element) => {
      registerConfirmationLink(element);
    },
  );
}

export default { init };
