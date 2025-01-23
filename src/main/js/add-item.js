// import $ from "jquery";

import { createElementFromHtml } from "@/util/dom";

var getItems = function () {
  return fetch("itemCategories?depth=3&iconStyle=icon-xlg")
    .then((response) => {
      if (!response.ok) {
        throw new Error("Network response was not ok " + response.statusText);
      }
      return response.json();
    });
};

var jRoot = document.querySelector("head").getAttribute("data-rooturl");

document.addEventListener("DOMContentLoaded", () => {
  getItems().then((data) => {
    //////////////////////////
    // helper functions...

    // function parseResponseFromCheckJobName(data) {
    //   var html = $.parseHTML(data);
    //   var element = html[0];
    //   if (element !== undefined) {
    //     return document.querySelector(element).text();
    //   }
    //   return undefined;
    // }

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
      return document.querySelector('input[type="text"][name="from"]', "#createItem").val();
    }

    function isItemNameEmpty() {
      var itemName = document.querySelector('input[name="name"]', "#createItem").val();
      return itemName === "" ? true : false;
    }

    function getFieldValidationStatus(fieldId) {
      return document.querySelector("#" + fieldId).data("valid");
    }

    function setFieldValidationStatus(fieldId, status) {
      document.querySelector("#" + fieldId).data("valid", status);
    }

    function activateValidationMessage(messageId, context, message) {
      if (message !== undefined && message !== "") {
        document.querySelector(context + " " + messageId).text("» " + message);
      }
      cleanValidationMessages(context);
      document.querySelector(messageId).classList.remove("input-message-disabled");
      enableSubmit(false);
    }

    function cleanValidationMessages(context) {
      document.querySelector(context)
        .querySelector(".input-validation-message")
        .classList.add("input-message-disabled");
    }

    function enableSubmit(status) {
      var btn = document.querySelector(".bottom-sticker-inner button[type=submit]");
      if (status === true) {
        if (btn.hasClass("disabled")) {
          btn.removeClass("disabled");
          btn.prop("disabled", false);
        }
      } else {
        if (!btn.hasClass("disabled")) {
          btn.addClass("disabled");
          btn.prop("disabled", true);
        }
      }
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
      document.querySelector(".categories").find('li[role="radio"]').attr("aria-checked", "false");
      document.querySelector("#createItem")
        .find('input[type="radio"][name="mode"]')
        .removeAttr("checked");
      document.querySelector(".categories").find(".active").removeClass("active");
      setFieldValidationStatus("items", false);
    }

    function cleanCopyFromOption() {
      document.querySelector("#createItem")
        .find('input[type="radio"][value="copy"]')
        .removeAttr("checked");
      document.querySelector('input[type="text"][name="from"]', "#createItem").val("");
      setFieldValidationStatus("from", false);
    }

    //////////////////////////////////
    // Draw functions

    function drawCategory(category) {
      var $category = createElementFromHtml("<div class='category' />")
        .attr("id", "j-add-item-type-" + cleanClassName(category.id));
      var $items = createElementFromHtml(`<ul class="j-item-options" />`);
      var $catHeader = createElementFromHtml(`<div class="header" />`);
      var title = "<h2>" + category.name + "</h2>";
      var description = "<p>" + category.description + "</p>";

      // Add items
      category.items.forEach((i, elem) => {
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

        // $(this).attr("aria-checked", "true");
        // $(this).find('input[type="radio"][name="mode"]').prop("checked", true);
        // $(this).addClass("active");

        item.setAttribute("aria-checked", "true");
        radio.checked = true;
        item.classList.add("active");

        setFieldValidationStatus("items", true);
        if (!getFieldValidationStatus("name")) {
          document.querySelector('input[name="name"][type="text"]', "#createItem").focus();
        } else {
          if (getFormValidationStatus()) {
            enableSubmit(true);
          }
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
        var colors = [
          "c-49728B",
          "c-335061",
          "c-D33833",
          "c-6D6B6D",
          "c-6699CC",
        ];
        var desc = elem.description || "";
        var name = elem.displayName;
        var colorClass = colors[desc.length % 4];
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
        iconDiv.className = colorClass + " default-icon";

        // Example for MockFolder
        // <div class="default-icon c-49728B"><span class="a">M</span><span class="b">o</span></div>
      }
      return iconDiv;
    }

    // The main panel content is hidden by default via an inline style. We're ready to remove that now.
    document.querySelector("#add-item-panel").removeAttr("style");

    // Render all categories
    var $categories = document.querySelector("div.categories");
    data.categories.forEach((i, elem) => {
      drawCategory(elem).appendTo($categories);
    });

    // Focus
    document.querySelector("#add-item-panel").find("#name").focus();

    // Init NameField
    document.querySelector('input[name="name"]', "#createItem").on("blur input", function () {
      if (!isItemNameEmpty()) {
        var itemName = document.querySelector('input[name="name"]', "#createItem").val();

        // TODO
        fetch(`checkJobName?value=${encodeURIComponent(itemName)}`).then(data => {
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
            if (getFormValidationStatus()) {
              enableSubmit(true);
            }
          }
        });
      } else {
        enableSubmit(false);
        setFieldValidationStatus("name", false);
        cleanValidationMessages(".add-item-name");
        activateValidationMessage("#itemname-required", ".add-item-name");
      }
    });

    // Init CopyFromField
    document.querySelector('input[name="from"]', "#createItem").on("blur input", function () {
      if (getCopyFromValue() === "") {
        document.querySelector("#createItem")
          .find('input[type="radio"][value="copy"]')
          .removeAttr("checked");
      } else {
        cleanItemSelection();
        document.querySelector("#createItem")
          .find('input[type="radio"][value="copy"]')
          .prop("checked", true);
        setFieldValidationStatus("from", true);
        if (!getFieldValidationStatus("name")) {
          activateValidationMessage("#itemname-required", ".add-item-name");
          setTimeout(function () {
            var parentName = document.querySelector('input[name="from"]', "#createItem").val();

            fetch("job/" + parentName + "/api/json?tree=name").then(data => {
                if (data.name === parentName) {
                  //if "name" is invalid, but "from" is a valid job, then switch focus to "name"
                  document.querySelector('input[name="name"][type="text"]', "#createItem").focus();
                }
              },
            );
          }, 400);
        } else {
          if (getFormValidationStatus()) {
            enableSubmit(true);
          }
        }
      }
    });

    // Client-side validation
    document.querySelector("#createItem").submit(function (event) {
      if (!getFormValidationStatus()) {
        event.preventDefault();
        if (!getFieldValidationStatus("name")) {
          activateValidationMessage("#itemname-required", ".add-item-name");
          document.querySelector('input[name="name"][type="text"]', "#createItem").focus();
        } else {
          if (
            !getFieldValidationStatus("items") &&
            !getFieldValidationStatus("from")
          ) {
            activateValidationMessage("#itemtype-required", ".add-item-name");
            document.querySelector('input[name="name"][type="text"]', "#createItem").focus();
          }
        }
      }
    });

    // Disable the submit button
    enableSubmit(false);
  });
});
