import { getI18n } from "@/util/i18n";
import { createElementFromHtml } from "@/util/dom";

const enableHeadings = false;
const nameInput = document.querySelector(`#createItem input[name="name"]`);
const copyFromInput = document.querySelector(`#createItem input[name="from"]`);
const copyRadio = document.querySelector(`#createItem input[value="copy"]`);

const getItems = function () {
  return fetch("itemCategories?depth=3&iconStyle=icon-xlg").then((response) =>
    response.json(),
  );
};

const jRoot = document.querySelector("head").getAttribute("data-rooturl");

document.addEventListener("DOMContentLoaded", () => {
  getItems().then((data) => {
    //////////////////////////
    // helper functions...

    function parseResponseFromCheckJobName(data) {
      var parser = new DOMParser();
      var html = parser.parseFromString(data, "text/html");
      var element = html.body.firstChild;
      if (element) {
        return element.textContent;
      }
      return undefined;
    }

    function cleanClassName(className) {
      return className.replace(/\./g, "_");
    }

    function checkForLink(desc) {
      if (desc.indexOf('&lt;a href="') === -1) {
        return desc;
      }
      // eslint-disable-next-line no-useless-escape
      var newDesc = desc.replace(/\&lt;/g, "<").replace(/\&gt;/g, ">");
      return newDesc;
    }

    function getCopyFromValue() {
      return copyFromInput.value;
    }

    function isItemNameEmpty() {
      var itemName = nameInput.value;
      return itemName.trim() === "";
    }

    function getFieldValidationStatus(fieldId) {
      return document.querySelector("#" + fieldId)?.dataset.valid === "true";
    }

    function setFieldValidationStatus(fieldId, status) {
      const element = document.querySelector("#" + fieldId);
      if (element) {
        element.dataset.valid = status;
      }
    }

    /**
     * Shows or clears the validation message for the name input.
     *
     * Only updates the UI after the user has interacted with the input, which is
     * indicated by `nameInput.dataset.dirty` being set.
     */
    function activateValidationMessage(message) {
      if (!nameInput.dataset.dirty) {
        return;
      }

      updateValidationArea(
        document.querySelector(".validation-error-area"),
        message !== undefined && message !== ""
          ? `<div class="error">${message}</div>`
          : `<div/>`,
      );

      refreshSubmitButtonState();
    }

    function refreshSubmitButtonState() {
      const submitButton = document.querySelector(
        ".bottom-sticker-inner button[type=submit]",
      );
      submitButton.disabled = !getFormValidationStatus();
    }

    function getFormValidationStatus() {
      if (
        getFieldValidationStatus("name") &&
        (getFieldValidationStatus("items") || getFieldValidationStatus("from"))
      ) {
        return true;
      }
      return false;
    }

    function cleanItemSelection() {
      document
        .querySelector('#createItem input[type="radio"][name="mode"]')
        .removeAttribute("checked");
      setFieldValidationStatus("items", false);
    }

    function cleanCopyFromOption() {
      copyRadio?.removeAttribute("checked");
      if (copyFromInput) {
        copyFromInput.value = "";
      }
      setFieldValidationStatus("from", false);
    }

    //////////////////////////////////
    // Draw functions

    function drawCategory(category) {
      const heading = createElementFromHtml(
        "<div class='jenkins-choice-list__heading'></div>",
      );
      const title = createElementFromHtml("<h2>" + category.name + "</h2>");
      const description = createElementFromHtml(
        "<p>" + category.description + "</p>",
      );
      heading.appendChild(title);
      heading.appendChild(description);
      const response = [];

      if (enableHeadings) {
        response.push(heading);
      }

      category.items.forEach((elem) => {
        response.push(drawItem(elem));
      });

      return response;
    }

    function drawItem(elem) {
      var item = document.createElement("div");
      item.className =
        cleanClassName(elem.class) + " jenkins-choice-list__item";

      var label = item.appendChild(document.createElement("label"));

      var iconDiv = drawIcon(elem);
      label.appendChild(iconDiv);

      var radio = label.appendChild(document.createElement("input"));
      radio.type = "radio";
      radio.name = "mode";
      radio.value = elem.class;

      var displayName = label.appendChild(document.createElement("span"));
      displayName.className = "jenkins-choice-list__item__label";
      displayName.appendChild(document.createTextNode(elem.displayName));

      var desc = label.appendChild(document.createElement("div"));
      desc.className = "jenkins-choice-list__item__description";
      desc.innerHTML = checkForLink(elem.description);

      function select() {
        cleanCopyFromOption();
        cleanItemSelection();
        setFieldValidationStatus("items", true);

        if (getFieldValidationStatus("name")) {
          refreshSubmitButtonState();
        }
      }

      radio.addEventListener("change", select);

      return item;
    }

    function drawIcon(elem) {
      var iconDiv = document.createElement("div");
      if (elem.iconXml) {
        iconDiv.className = "jenkins-choice-list__item__icon";
        iconDiv.innerHTML = elem.iconXml;
      } else if (elem.iconClassName && elem.iconQualifiedUrl) {
        iconDiv.className = "jenkins-choice-list__item__icon";

        var img1 = document.createElement("img");
        img1.src = elem.iconQualifiedUrl;
        iconDiv.appendChild(img1);

        // Example for Freestyle project
        // <div class="icon"><img class="icon-freestyle-project icon-xlg" src="/jenkins/static/108b2346/images/48x48/freestyleproject.png"></div>
      } else if (elem.iconFilePathPattern) {
        iconDiv.className = "jenkins-choice-list__item__icon";

        var iconFilePath =
          jRoot + "/" + elem.iconFilePathPattern.replace(":size", "48x48");

        var img2 = document.createElement("img");
        img2.src = iconFilePath;
        iconDiv.appendChild(img2);

        // Example for Maven project
        // <div class="icon"><img src="/jenkins/plugin/maven-plugin/images/48x48/mavenmoduleset.png"></div>
      } else {
        var name = elem.displayName;
        var aName = name.split(" ");
        var a = name.substring(0, 1);
        var b =
          aName.length === 1 ? name.substring(1, 2) : aName[1].substring(0, 1);

        var spanFakeImgA = document.createElement("span");
        spanFakeImgA.className = "a";
        spanFakeImgA.innerText = a;
        iconDiv.appendChild(spanFakeImgA);
        var spanFakeImgB = document.createElement("span");
        spanFakeImgB.className = "b";
        spanFakeImgB.innerText = b;
        iconDiv.appendChild(spanFakeImgB);
        iconDiv.className = "jenkins-choice-list__item__icon";

        // Example for MockFolder
        // <div class="default-icon c-49728B"><span class="a">M</span><span class="b">o</span></div>
      }
      return iconDiv;
    }

    // The main panel content is hidden by default via an inline style. We're ready to remove that now.
    document.querySelector("#add-item-panel").removeAttribute("style");

    // Render all categories
    var $categories = document.querySelector(".categories");
    data.categories.forEach((elem) => {
      drawCategory(elem).forEach((e) => $categories.append(e));
    });

    // Init NameField
    function nameFieldEvent() {
      if (!isItemNameEmpty()) {
        var itemName = nameInput.value;

        fetch(`checkJobName?value=${encodeURIComponent(itemName)}`).then(
          (response) => {
            response.text().then((data) => {
              var message = parseResponseFromCheckJobName(data);
              if (message !== "") {
                activateValidationMessage(message);
                setFieldValidationStatus("name", false);
                refreshSubmitButtonState();
              } else {
                activateValidationMessage("");
                setFieldValidationStatus("name", true);
                refreshSubmitButtonState();
              }
            });
          },
        );
      } else {
        setFieldValidationStatus("name", false);
        activateValidationMessage(getI18n("empty-name"));
        refreshSubmitButtonState();
      }
    }

    nameInput.addEventListener("blur", nameFieldEvent);
    nameInput.addEventListener("input", () => {
      nameInput.dataset.dirty = "true";
      nameFieldEvent();
    });

    // Init CopyFromField
    function copyFromFieldEvent() {
      if (getCopyFromValue() === "") {
        copyRadio.removeAttribute("checked");
      } else {
        cleanItemSelection();
        copyRadio.setAttribute("checked", true);
        setFieldValidationStatus("from", true);
        refreshSubmitButtonState();
      }
    }

    copyFromInput?.addEventListener("blur", copyFromFieldEvent);
    copyFromInput?.addEventListener("input", copyFromFieldEvent);

    // Focus the Name input on load
    document.querySelector("#add-item-panel #name").focus();

    // Disable the Submit button on load
    refreshSubmitButtonState();
  });

  if (copyRadio !== null) {
    copyRadio.addEventListener("change", () => {
      copyFromInput.focus();
    });
  }
});
