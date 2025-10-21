import { createElementFromHtml } from "@/util/dom";

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

    function activateValidationMessage(messageId, context, message) {
      if (message !== undefined && message !== "") {
        document.querySelector(context + " " + messageId).textContent =
          "» " + message;
      }
      cleanValidationMessages(context);
      document
        .querySelector(messageId)
        .classList.remove("input-message-disabled");
      refreshSubmitButtonState();
    }

    function cleanValidationMessages(context) {
      document
        .querySelectorAll(context + " .input-validation-message")
        .forEach((element) => element.classList.add("input-message-disabled"));
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
        .querySelector('.categories li[role="radio"]')
        .setAttribute("aria-checked", "false");
      document
        .querySelector('#createItem input[type="radio"][name="mode"]')
        .removeAttribute("checked");
      document.querySelectorAll(".categories .active").forEach((item) => {
        item.classList.remove("active");
      });
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
      var $category = createElementFromHtml("<div class='category' />");
      $category.setAttribute(
        "id",
        "j-add-item-type-" + cleanClassName(category.id),
      );
      var $items = createElementFromHtml(`<ul class="j-item-options" />`);
      var $catHeader = createElementFromHtml(`<div class="header" />`);
      var title = "<h2>" + category.name + "</h2>";
      var description = "<p>" + category.description + "</p>";

      // Add items
      category.items.forEach((elem) => {
        $items.append(drawItem(elem));
      });

      $catHeader.append(title);
      $catHeader.append(description);
      $category.append($catHeader);
      $category.append($items);

      return $category;
    }

    function drawItem(elem) {
      var item = document.createElement("li");
      item.tabIndex = 0;
      item.className = cleanClassName(elem.class);
      item.setAttribute("role", "radio");
      item.setAttribute("aria-checked", "false");

      var iconDiv = drawIcon(elem);
      item.appendChild(iconDiv);
      var labelContainer = document.createElement("div");
      item.appendChild(labelContainer);

      var label = labelContainer.appendChild(document.createElement("label"));

      var radio = label.appendChild(document.createElement("input"));
      radio.type = "radio";
      radio.name = "mode";
      radio.value = elem.class;

      var displayName = label.appendChild(document.createElement("span"));
      displayName.className = "label";

      displayName.appendChild(document.createTextNode(elem.displayName));

      var desc = labelContainer.appendChild(document.createElement("div"));
      desc.className = "desc";
      desc.innerHTML = checkForLink(elem.description);

      function select(e) {
        e.preventDefault();
        cleanCopyFromOption();
        cleanItemSelection();

        item.setAttribute("aria-checked", "true");
        radio.checked = true;
        item.classList.add("active");

        setFieldValidationStatus("items", true);
        if (getFieldValidationStatus("name")) {
          refreshSubmitButtonState();
        }
      }

      item.addEventListener("click", select);
      item.addEventListener("keydown", function (evt) {
        if (evt.code === "Space" || evt.code === "Enter") {
          this.click();
          evt.stopPropagation();
        }
      });

      return item;
    }

    function drawIcon(elem) {
      var iconDiv = document.createElement("div");
      if (elem.iconXml) {
        iconDiv.className = "icon";
        iconDiv.innerHTML = elem.iconXml;
      } else if (elem.iconClassName && elem.iconQualifiedUrl) {
        iconDiv.className = "icon";

        var img1 = document.createElement("img");
        img1.src = elem.iconQualifiedUrl;
        iconDiv.appendChild(img1);

        // Example for Freestyle project
        // <div class="icon"><img class="icon-freestyle-project icon-xlg" src="/jenkins/static/108b2346/images/48x48/freestyleproject.png"></div>
      } else if (elem.iconFilePathPattern) {
        iconDiv.className = "icon";

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
        iconDiv.className = "default-icon";

        // Example for MockFolder
        // <div class="default-icon c-49728B"><span class="a">M</span><span class="b">o</span></div>
      }
      return iconDiv;
    }

    // The main panel content is hidden by default via an inline style. We're ready to remove that now.
    document.querySelector("#add-item-panel").removeAttribute("style");

    // Render all categories
    var $categories = document.querySelector("div.categories");
    data.categories.forEach((elem) => {
      $categories.append(drawCategory(elem));
    });

    // Focus
    document.querySelector("#add-item-panel #name").focus();

    // Init NameField
    function nameFieldEvent() {
      if (!isItemNameEmpty()) {
        var itemName = nameInput.value;

        fetch(`checkJobName?value=${encodeURIComponent(itemName)}`).then(
          (response) => {
            response.text().then((data) => {
              var message = parseResponseFromCheckJobName(data);
              if (message !== "") {
                activateValidationMessage(
                  "#itemname-invalid",
                  ".add-item-name",
                  message,
                );
              } else {
                cleanValidationMessages(".add-item-name");
                setFieldValidationStatus("name", true);
                refreshSubmitButtonState();
              }
            });
          },
        );
      } else {
        setFieldValidationStatus("name", false);
        cleanValidationMessages(".add-item-name");
        activateValidationMessage("#itemname-required", ".add-item-name");
        refreshSubmitButtonState();
      }
    }

    nameInput.addEventListener("blur", nameFieldEvent);
    nameInput.addEventListener("input", nameFieldEvent);

    // Init CopyFromField
    function copyFromFieldEvent() {
      if (getCopyFromValue() === "") {
        copyRadio.removeAttribute("checked");
      } else {
        cleanItemSelection();
        copyRadio.setAttribute("checked", true);
        setFieldValidationStatus("from", true);
        if (!getFieldValidationStatus("name")) {
          activateValidationMessage("#itemname-required", ".add-item-name");
          setTimeout(function () {
            var parentName = copyFromInput.value;

            fetch("job/" + parentName + "/api/json?tree=name").then(
              (response) => {
                response.json().then((data) => {
                  if (data.name === parentName) {
                    //if "name" is invalid, but "from" is a valid job, then switch focus to "name"
                    nameInput.focus();
                  }
                });
              },
            );
          }, 400);
        } else {
          refreshSubmitButtonState();
        }
      }
    }

    copyFromInput?.addEventListener("blur", copyFromFieldEvent);
    copyFromInput?.addEventListener("input", copyFromFieldEvent);

    // Client-side validation
    document
      .querySelector("#createItem")
      .addEventListener("submit", function (event) {
        if (!getFormValidationStatus()) {
          event.preventDefault();
          if (!getFieldValidationStatus("name")) {
            activateValidationMessage("#itemname-required", ".add-item-name");
            nameInput.focus();
          } else {
            if (
              !getFieldValidationStatus("items") &&
              !getFieldValidationStatus("from")
            ) {
              activateValidationMessage("#itemtype-required", ".add-item-name");
              nameInput.focus();
            }
          }
        }
      });

    // Disable the submit button
    refreshSubmitButtonState();
  });
});
