import { showModal } from "@/components/modals";
import behaviorShim from "@/util/behavior-shim";

function init() {
  behaviorShim.specify(
    ".stop-button-link",
    "stop-button-link",
    0,
    function (link) {
      let question = link.getAttribute("data-confirm");
      let url = link.getAttribute("href");
      link.addEventListener("click", function (e) {
        e.preventDefault();
        var execute = function () {
          new Ajax.Request(url);
        };
        if (question !== null) {
          showModal(question, {
            okButton: "Yes",
            cancelButton: "Cancel",
            hideCloseButton: true,
            callback: execute,
            okButtonColor: "jenkins-!-destructive-color",
            closeOnClick: false,
          });
        } else {
          execute();
        }
      });
    }
  );
}

export default { init };
