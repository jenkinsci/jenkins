/* global replaceDescription */
(function () {
  document.addEventListener("DOMContentLoaded", function () {
    const descriptionLink = document.querySelector("#description-link");
    const description = document.getElementById("description");
    if (description != null) {
      descriptionLink.classList.remove("jenkins-hidden");
      descriptionLink.addEventListener("click", function (e) {
        e.preventDefault();
        descriptionLink.classList.add("jenkins-hidden");
        let url = descriptionLink.getAttribute("data-url");
        let description = descriptionLink.getAttribute("data-description");
        return replaceDescription(description, url);
      });
    }
  });

  Behaviour.specify(".description-cancel-button", "description-cancel-button", 0, function (b) {
    b.onclick = function() {
      const descriptionLink = document.getElementById("description-link");
      const descriptionContent = document.getElementById("description-content");
      const descriptionEditForm = document.getElementById("description-edit-form");
      descriptionEditForm.innerHTML = "";
      descriptionEditForm.classList.add("jenkins-hidden");
      descriptionContent.classList.remove("jenkins-hidden");
      descriptionLink.classList.remove("jenkins-hidden");
    };
  });

  function replaceDescription(initialDescription, submissionUrl) {
    const descriptionContent = document.getElementById("description-content");
    const descriptionEditForm = document.getElementById("description-edit-form");
    descriptionEditForm.innerHTML = "<div class='jenkins-spinner'></div>";
    descriptionContent.classList.add("jenkins-hidden");
    let parameters = {};
    if (initialDescription !== null && initialDescription !== "") {
      parameters["description"] = initialDescription;
    }
    if (submissionUrl !== null && submissionUrl !== "") {
      parameters["submissionUrl"] = submissionUrl;
    }
    fetch("./descriptionForm", {
      method: "post",
      headers: crumb.wrap({
        "Content-Type": "application/x-www-form-urlencoded",
      }),
      body: objectToUrlFormEncoded(parameters),
    }).then((rsp) => {
      rsp.text().then((responseText) => {
        descriptionEditForm.innerHTML = responseText;
        descriptionEditForm.classList.remove("jenkins-hidden");
        evalInnerHtmlScripts(responseText, function () {
          Behaviour.applySubtree(descriptionEditForm);
          descriptionEditForm.getElementsByTagName("TEXTAREA")[0].focus();
        });
        layoutUpdateCallback.call();
        return false;
      });
    });
  }
})();
