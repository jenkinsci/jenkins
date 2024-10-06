import $ from "jquery";

var getItems = function () {
  var d = $.Deferred();
  $.get("itemCategories?depth=3&iconStyle=icon-xlg").done(function (data) {
    d.resolve(data);
  });
  return d.promise();
};

var jRoot = $("head").attr("data-rooturl");

$.when(getItems()).done(function (data) {
  $(function () {
    //////////////////////////
    // helper functions...

    function parseResponseFromCheckJobName(data) {
      var html = $.parseHTML(data);
      var element = html[0];
      if (element !== undefined) {
        return $(element).text();
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
      return $('input[type="text"][name="from"]', "#createItem").val();
    }

    function isItemNameEmpty() {
      var itemName = $('input[name="name"]', "#createItem").val();
      return itemName === "" ? true : false;
    }

    function getFieldValidationStatus(fieldId) {
      return $("#" + fieldId).data("valid");
    }

    function setFieldValidationStatus(fieldId, status) {
      $("#" + fieldId).data("valid", status);
    }

    function activateValidationMessage(messageId, context, message) {
      if (message !== undefined && message !== "") {
        $(messageId, context).text("» " + message);
      }
      cleanValidationMessages(context);
      $(messageId).removeClass("input-message-disabled");
      enableSubmit(false);
    }

    function cleanValidationMessages(context) {
      $(context)
        .find(".input-validation-message")
        .addClass("input-message-disabled");
    }

    function enableSubmit(status) {
      var btn = $(".bottom-sticker-inner button[type=submit]");
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
      $(".categories").find('li[role="radio"]').attr("aria-checked", "false");
      $("#createItem")
        .find('input[type="radio"][name="mode"]')
        .removeAttr("checked");
      $(".categories").find(".active").removeClass("active");
      setFieldValidationStatus("items", false);
    }

    function cleanCopyFromOption() {
      $("#createItem")
        .find('input[type="radio"][value="copy"]')
        .removeAttr("checked");
      $('input[type="text"][name="from"]', "#createItem").val("");
      setFieldValidationStatus("from", false);
    }

    //////////////////////////////////
    // Draw functions

    function drawCategory(category) {
      var $category = $("<div/>")
        .addClass("category")
        .attr("id", "j-add-item-type-" + cleanClassName(category.id));
      var $items = $("<ul/>").addClass("j-item-options");
      var $catHeader = $('<div class="header" />');
      var title = "<h2>" + category.name + "</h2>";
      var description = "<p>" + category.description + "</p>";

      // Add items
      $.each(category.items, function (i, elem) {
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

        $(this).attr("aria-checked", "true");
        $(this).find('input[type="radio"][name="mode"]').prop("checked", true);
        $(this).addClass("active");

        setFieldValidationStatus("items", true);
        if (!getFieldValidationStatus("name")) {
          $('input[name="name"][type="text"]', "#createItem").focus();
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
    $("#add-item-panel").removeAttr("style");

    // Render all categories
    var $categories = $("div.categories");
    $.each(data.categories, function (i, elem) {
      drawCategory(elem).appendTo($categories);
    });

    // Focus
    $("#add-item-panel").find("#name").focus();

    // Init NameField
    $('input[name="name"]', "#createItem").on("blur input", function () {
      if (!isItemNameEmpty()) {
        var itemName = $('input[name="name"]', "#createItem").val();
        $.get("checkJobName", { value: itemName }).done(function (data) {
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
    $('input[name="from"]', "#createItem").on("blur input", function () {
      if (getCopyFromValue() === "") {
        $("#createItem")
          .find('input[type="radio"][value="copy"]')
          .removeAttr("checked");
      } else {
        cleanItemSelection();
        $("#createItem")
          .find('input[type="radio"][value="copy"]')
          .prop("checked", true);
        setFieldValidationStatus("from", true);
        if (!getFieldValidationStatus("name")) {
          activateValidationMessage("#itemname-required", ".add-item-name");
          setTimeout(function () {
            var parentName = $('input[name="from"]', "#createItem").val();
            $.get("job/" + parentName + "/api/json?tree=name").done(
              function (data) {
                if (data.name === parentName) {
                  //if "name" is invalid, but "from" is a valid job, then switch focus to "name"
                  $('input[name="name"][type="text"]', "#createItem").focus();
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
    $("#createItem").submit(function (event) {
      if (!getFormValidationStatus()) {
        event.preventDefault();
        if (!getFieldValidationStatus("name")) {
          activateValidationMessage("#itemname-required", ".add-item-name");
          $('input[name="name"][type="text"]', "#createItem").focus();
        } else {
          if (
            !getFieldValidationStatus("items") &&
            !getFieldValidationStatus("from")
          ) {
            activateValidationMessage("#itemtype-required", ".add-item-name");
            $('input[name="name"][type="text"]', "#createItem").focus();
          }
        }
      }
    });

    // Disable the submit button
    enableSubmit(false);
  });
});
