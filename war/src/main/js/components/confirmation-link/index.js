import { confirmationLink } from "@/components/modals";
import behaviorShim from "@/util/behavior-shim";

behaviorShim.specify(
  "A.confirmation-link",
  "confirmation-link",
  0,
  function (element) {
    element.onclick = function () {
      var post = element.getAttribute("data-post");
      var href = element.getAttribute("data-url");
      var message = element.getAttribute("data-message");
      var title = element.getAttribute("data-title");
      confirmationLink(message, title, href, post);
      return false;
    };
  }
);
