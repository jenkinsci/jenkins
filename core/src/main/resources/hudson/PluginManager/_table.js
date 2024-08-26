Behaviour.specify("#filter-box", "_table", 0, function (e) {
  function applyFilter() {
    var filter = e.value.toLowerCase().trim();
    var filterParts = filter.split(/ +/).filter(function (word) {
      return word.length > 0;
    });
    var items = document
      .getElementsBySelector("TR.plugin")
      .concat(document.getElementsBySelector("TR.unavailable"));
    var anyVisible = false;
    for (var i = 0; i < items.length; i++) {
      if (
        (filterParts.length < 1 || filter.length < 2) &&
        items[i].classList.contains("hidden-by-default")
      ) {
        items[i].classList.add("jenkins-hidden");
        continue;
      }
      var makeVisible = true;

      var pluginId = items[i].getAttribute("data-plugin-id");
      var content = (
        items[i].querySelector(".details").innerText +
        " " +
        pluginId
      ).toLowerCase();

      for (var j = 0; j < filterParts.length; j++) {
        var part = filterParts[j];
        if (content.indexOf(part) < 0) {
          makeVisible = false;
          break;
        }
      }

      if (makeVisible) {
        items[i].classList.remove("jenkins-hidden");
        anyVisible = true;
      } else {
        items[i].classList.add("jenkins-hidden");
      }
    }
    var instructions = document.getElementById(
      "hidden-by-default-instructions",
    );
    if (instructions) {
      instructions.style.display = anyVisible ? "none" : "";
    }

    layoutUpdateCallback.call();
  }
  e.addEventListener("input", () => applyFilter());

  (function () {
    var instructionsTd = document.getElementById(
      "hidden-by-default-instructions-td",
    );
    if (instructionsTd) {
      // only on Available tab
      instructionsTd.innerText =
        instructionsTd.getAttribute("data-loaded-text");
    }
    applyFilter();
  })();
});

/**
 * Code for handling the enable/disable behavior based on plugin
 * dependencies and dependents.
 */
(function () {
  function selectAll(selector, element) {
    if (element) {
      return element.querySelectorAll(selector);
    } else {
      return document.querySelectorAll(selector);
    }
  }
  function select(selector, element) {
    var elementsBySelector = selectAll(selector, element);
    if (elementsBySelector.length > 0) {
      return elementsBySelector[0];
    } else {
      return undefined;
    }
  }

  /**
   * Wait for document onload.
   */
  window.addEventListener("load", function () {
    var pluginsTable = select("#plugins");
    var pluginTRs = selectAll(".plugin", pluginsTable);

    if (!pluginTRs) {
      return;
    }

    // Create a map of the plugin rows, making it easy to index them.
    var plugins = {};
    for (var i = 0; i < pluginTRs.length; i++) {
      var pluginTR = pluginTRs[i];
      var pluginId = pluginTR.getAttribute("data-plugin-id");

      plugins[pluginId] = pluginTR;
    }

    function getPluginTR(pluginId) {
      return plugins[pluginId];
    }
    function getPluginName(pluginId) {
      var pluginTR = getPluginTR(pluginId);
      if (pluginTR) {
        return pluginTR.getAttribute("data-plugin-name");
      } else {
        return pluginId;
      }
    }

    function processSpanSet(spans) {
      var ids = [];
      for (var i = 0; i < spans.length; i++) {
        var span = spans[i];
        var pluginId = span.getAttribute("data-plugin-id");
        var pluginName = getPluginName(pluginId);

        span.textContent = pluginName;
        ids.push(pluginId);
      }
      return ids;
    }

    function markAllDependentsDisabled(pluginTR) {
      var jenkinsPluginMetadata = pluginTR.jenkinsPluginMetadata;
      var dependentIds = jenkinsPluginMetadata.dependentIds;

      if (dependentIds) {
        // If the only dependent is jenkins-core (it's a bundle plugin), then lets
        // treat it like all its dependents are disabled. We're really only interested in
        // dependent plugins in this case.
        // Note: This does not cover "implied" dependencies ala detached plugins. See https://goo.gl/lQHrUh
        if (dependentIds.length === 1 && dependentIds[0] === "jenkins-core") {
          pluginTR.classList.add("all-dependents-disabled");
          return;
        }

        for (var i = 0; i < dependentIds.length; i++) {
          var dependentId = dependentIds[i];

          if (dependentId === "jenkins-core") {
            // Jenkins core is always enabled. So, make sure it's not possible to disable/uninstall
            // any plugins that it "depends" on. (we sill have bundled plugins)
            pluginTR.classList.remove("all-dependents-disabled");
            return;
          }

          // The dependent is a plugin....
          var dependentPluginTr = getPluginTR(dependentId);
          if (
            dependentPluginTr &&
            dependentPluginTr.jenkinsPluginMetadata.enableInput.checked
          ) {
            // One of the plugins that depend on this plugin, is marked as enabled.
            pluginTR.classList.remove("all-dependents-disabled");
            return;
          }
        }
      }

      pluginTR.classList.add("all-dependents-disabled");
    }

    function markHasDisabledDependencies(pluginTR) {
      var jenkinsPluginMetadata = pluginTR.jenkinsPluginMetadata;
      var dependencyIds = jenkinsPluginMetadata.dependencyIds;

      if (dependencyIds) {
        for (var i = 0; i < dependencyIds.length; i++) {
          var dependencyPluginTr = getPluginTR(dependencyIds[i]);
          if (
            dependencyPluginTr &&
            !dependencyPluginTr.jenkinsPluginMetadata.enableInput.checked
          ) {
            // One of the plugins that this plugin depend on, is marked as disabled.
            pluginTR.classList.add("has-disabled-dependency");
            return;
          }
        }
      }

      pluginTR.classList.remove("has-disabled-dependency");
    }

    function setEnableWidgetStates() {
      for (var i = 0; i < pluginTRs.length; i++) {
        var pluginMetadata = pluginTRs[i].jenkinsPluginMetadata;
        if (pluginTRs[i].classList.contains("has-dependents-but-disabled")) {
          if (pluginMetadata.enableInput.checked) {
            pluginTRs[i].classList.remove("has-dependents-but-disabled");
          }
        }
        markAllDependentsDisabled(pluginTRs[i]);
        markHasDisabledDependencies(pluginTRs[i]);
      }
    }

    function addDependencyInfoRow(pluginTR, infoTR) {
      infoTR.classList.add("plugin-dependency-info");
      pluginTR.parentNode.insertBefore(infoTR, pluginTR.nextElementSibling);
    }
    function removeDependencyInfoRow(pluginTR) {
      var nextRow = pluginTR.nextElementSibling;
      if (nextRow && nextRow.classList.contains("plugin-dependency-info")) {
        nextRow.remove();
      }
    }

    function populateEnableDisableInfo(pluginTR, infoContainer) {
      var pluginMetadata = pluginTR.jenkinsPluginMetadata;

      // Remove all existing class info
      infoContainer.removeAttribute("class");
      infoContainer.classList.add("enable-state-info");

      if (pluginTR.classList.contains("has-disabled-dependency")) {
        var dependenciesDiv = pluginMetadata.dependenciesDiv;
        var dependencySpans = pluginMetadata.dependencies;

        infoContainer.innerHTML =
          '<div class="title">' +
          i18n("cannot-enable") +
          '</div><div class="subtitle">' +
          i18n("disabled-dependencies") +
          ".</div>";

        // Go through each dependency <span> element. Show the spans where the dependency is
        // disabled. Hide the others.
        for (var i = 0; i < dependencySpans.length; i++) {
          var dependencySpan = dependencySpans[i];
          var pluginId = dependencySpan.getAttribute("data-plugin-id");
          var depPluginTR = getPluginTR(pluginId);
          var enabled = false;
          if (depPluginTR) {
            var depPluginMetadata = depPluginTR.jenkinsPluginMetadata;
            enabled = depPluginMetadata.enableInput.checked;
          }
          if (enabled) {
            // It's enabled ... hide the span
            dependencySpan.style.display = "none";
          } else {
            // It's disabled ... show the span
            dependencySpan.style.display = "inline-block";
          }
        }

        dependenciesDiv.style.display = "inherit";
        infoContainer.appendChild(dependenciesDiv);

        return true;
      }
      if (pluginTR.classList.contains("has-dependents")) {
        if (!pluginTR.classList.contains("all-dependents-disabled")) {
          var dependentIds = pluginMetadata.dependentIds;

          // If the only dependent is jenkins-core (it's a bundle plugin), then lets
          // treat it like all its dependents are disabled. We're really only interested in
          // dependent plugins in this case.
          // Note: This does not cover "implied" dependencies ala detached plugins. See https://goo.gl/lQHrUh
          if (dependentIds.length === 1 && dependentIds[0] === "jenkins-core") {
            pluginTR.classList.add("all-dependents-disabled");
            return false;
          }

          infoContainer.innerHTML =
            '<div class="title">' +
            i18n("cannot-disable") +
            '</div><div class="subtitle">' +
            i18n("enabled-dependents") +
            ".</div>";
          infoContainer.appendChild(getDependentsDiv(pluginTR, true));
          return true;
        }
      }

      if (pluginTR.classList.contains("possibly-has-implied-dependents")) {
        infoContainer.innerHTML =
          '<div class="title">' +
          i18n("detached-disable") +
          '</div><div class="subtitle">' +
          i18n("detached-possible-dependents") +
          "</div>";
        infoContainer.appendChild(getDependentsDiv(pluginTR, true));
        return true;
      }

      return false;
    }

    function populateUninstallInfo(pluginTR, infoContainer) {
      // Remove all existing class info
      infoContainer.removeAttribute("class");
      infoContainer.classList.add("uninstall-state-info");

      if (pluginTR.classList.contains("has-dependents")) {
        infoContainer.innerHTML =
          '<div class="title">' +
          i18n("cannot-uninstall") +
          '</div><div class="subtitle">' +
          i18n("installed-dependents") +
          ".</div>";
        infoContainer.appendChild(getDependentsDiv(pluginTR, false));
        return true;
      }

      if (pluginTR.classList.contains("possibly-has-implied-dependents")) {
        infoContainer.innerHTML =
          '<div class="title">' +
          i18n("detached-uninstall") +
          '</div><div class="subtitle">' +
          i18n("detached-possible-dependents") +
          "</div>";
        infoContainer.appendChild(getDependentsDiv(pluginTR, false));
        return true;
      }

      return false;
    }

    function getDependentsDiv(pluginTR, hideDisabled) {
      var pluginMetadata = pluginTR.jenkinsPluginMetadata;
      var dependentsDiv = pluginMetadata.dependentsDiv;
      var dependentSpans = pluginMetadata.dependents;

      // Go through each dependent <span> element. If disabled should be hidden, show the spans where
      // the dependent is enabled and hide the others. Otherwise show them all.
      for (var i = 0; i < dependentSpans.length; i++) {
        var dependentSpan = dependentSpans[i];
        var dependentId = dependentSpan.getAttribute("data-plugin-id");

        if (!hideDisabled || dependentId === "jenkins-core") {
          dependentSpan.style.display = "inline-block";
        } else {
          var depPluginTR = getPluginTR(dependentId);
          var depPluginMetadata = depPluginTR.jenkinsPluginMetadata;
          if (depPluginMetadata.enableInput.checked) {
            // It's enabled ... show the span
            dependentSpan.style.display = "inline-block";
          } else {
            // It's disabled ... hide the span
            dependentSpan.style.display = "none";
          }
        }
      }

      dependentsDiv.style.display = "inherit";
      return dependentsDiv;
    }

    function initPluginRowHandling(pluginTR) {
      var enableInput = select(".enable input", pluginTR);
      var dependenciesDiv = select(".dependency-list", pluginTR);
      var dependentsDiv = select(".dependent-list", pluginTR);
      var enableTD = select("td.enable", pluginTR);
      var uninstallTD = select("td.uninstall", pluginTR);

      pluginTR.jenkinsPluginMetadata = {
        enableInput: enableInput,
        dependenciesDiv: dependenciesDiv,
        dependentsDiv: dependentsDiv,
      };

      if (dependenciesDiv) {
        pluginTR.jenkinsPluginMetadata.dependencies = selectAll(
          "span",
          dependenciesDiv,
        );
        pluginTR.jenkinsPluginMetadata.dependencyIds = processSpanSet(
          pluginTR.jenkinsPluginMetadata.dependencies,
        );
      }
      if (dependentsDiv) {
        pluginTR.jenkinsPluginMetadata.dependents = selectAll(
          "span",
          dependentsDiv,
        );
        pluginTR.jenkinsPluginMetadata.dependentIds = processSpanSet(
          pluginTR.jenkinsPluginMetadata.dependents,
        );
      }

      // Setup event handlers...
      if (enableInput) {
        // Toggling of the enable/disable checkbox requires a check and possible
        // change of visibility on the same checkbox on other plugins.
        enableInput.addEventListener("click", function () {
          setEnableWidgetStates();
        });
      }

      //
      var infoTR = document.createElement("tr");
      var infoTD = document.createElement("td");
      var infoDiv = document.createElement("div");
      infoTR.appendChild(infoTD);
      infoTD.appendChild(infoDiv);
      infoTD.setAttribute("colspan", "6"); // This is the cell that all info will be added to.
      infoDiv.style.display = "inherit";

      // We don't want the info row to appear immediately. We wait for e.g. 1 second and if the mouse
      // is still in there (hasn't left the cell) then we show. The following code is for clearing the
      // show timeout where the mouse has left before the timeout has fired.
      var showInfoTimeout = undefined;
      function clearShowInfoTimeout() {
        if (showInfoTimeout) {
          clearTimeout(showInfoTimeout);
        }
        showInfoTimeout = undefined;
      }

      // Handle mouse in/out of the enable/disable cell (left most cell).
      if (enableTD) {
        enableTD.addEventListener("mouseenter", function () {
          showInfoTimeout = setTimeout(function () {
            showInfoTimeout = undefined;
            infoDiv.textContent = "";
            if (populateEnableDisableInfo(pluginTR, infoDiv)) {
              addDependencyInfoRow(pluginTR, infoTR);
            }
          }, 1000);
        });
        enableTD.addEventListener("mouseleave", function () {
          clearShowInfoTimeout();
          removeDependencyInfoRow(pluginTR);
        });
      }

      // Handle mouse in/out of the uninstall cell (right most cell).
      if (uninstallTD) {
        uninstallTD.addEventListener("mouseenter", function () {
          showInfoTimeout = setTimeout(function () {
            showInfoTimeout = undefined;
            infoDiv.textContent = "";
            if (populateUninstallInfo(pluginTR, infoDiv)) {
              addDependencyInfoRow(pluginTR, infoTR);
            }
          }, 1000);
        });
        uninstallTD.addEventListener("mouseleave", function () {
          clearShowInfoTimeout();
          removeDependencyInfoRow(pluginTR);
        });
      }
    }

    for (let j = 0; j < pluginTRs.length; j++) {
      initPluginRowHandling(pluginTRs[j]);
    }

    setEnableWidgetStates();
  });
})();

window.addEventListener("load", function () {
  const compatibleCheckbox = document.querySelector(
    "[data-select='compatible']",
  );
  if (compatibleCheckbox) {
    compatibleCheckbox.addEventListener("click", () => {
      const inputs = document.getElementsByTagName("input");
      for (let i = 0; i < inputs.length; i++) {
        const candidate = inputs[i];
        if (candidate.type === "checkbox" && !candidate.disabled) {
          candidate.checked = candidate.dataset.compatWarning === "false";
        }
      }
      window.updateTableHeaderCheckbox();
    });
  }

  const uninstallButtons = document.querySelectorAll(
    "[data-action='uninstall']",
  );
  uninstallButtons.forEach((uninstallButton) => {
    uninstallButton.addEventListener("click", () => {
      const title = uninstallButton.dataset.message;
      const href = uninstallButton.dataset.href;

      const options = {
        message: i18n("uninstall-description"),
        type: "destructive",
      };

      dialog.confirm(title, options).then(
        () => {
          var form = document.createElement("form");
          form.setAttribute("method", "POST");
          form.setAttribute("action", href);
          crumb.appendToForm(form);
          document.body.appendChild(form);
          form.submit();
        },
        () => {},
      );
    });
  });

  const updateButton = document.querySelector("#button-update");
  if (updateButton) {
    // Enable/disable the 'Update' button depending on if any updates are checked
    const anyCheckboxesSelected = () => {
      return (
        document.querySelectorAll(
          "input[type='checkbox']:checked:not(:disabled)",
        ).length > 0
      );
    };
    const checkboxes = document.querySelectorAll(
      "input[type='checkbox'], [data-select], .jenkins-table__checkbox",
    );
    checkboxes.forEach((checkbox) => {
      checkbox.addEventListener("click", () => {
        setTimeout(() => {
          updateButton.disabled = !anyCheckboxesSelected();
        });
      });
    });
  }

  // Show update center error if element exists
  const updateCenterError = document.querySelector("#update-center-error");
  if (updateCenterError) {
    notificationBar.show(
      updateCenterError.content.textContent,
      notificationBar.ERROR,
    );
  }
});

function i18n(messageId) {
  return document.querySelector("#i18n").getAttribute("data-" + messageId);
}
