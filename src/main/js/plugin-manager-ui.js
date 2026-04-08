import debounce from "lodash/debounce";

import pluginManagerAvailable from "./templates/plugin-manager/available.hbs";
import pluginManager from "./api/pluginManager";

function applyFilter(searchQuery) {
  // debounce reduces number of server side calls while typing
  pluginManager.availablePluginsSearch(
    searchQuery.toLowerCase().trim(),
    50,
    function (plugins) {
      var pluginsTable = document.getElementById("plugins");
      var tbody = pluginsTable.querySelector("tbody");
      var admin = pluginsTable.dataset.hasadmin === "true";
      var includeHealthScores = pluginsTable.dataset.health === "true";
      var selectedPlugins = [];

      var filterInput = document.getElementById("filter-box");
      filterInput.parentElement.classList.remove("jenkins-search--loading");

      function clearOldResults() {
        if (!admin) {
          tbody.innerHTML = "";
        } else {
          var rows = tbody.querySelectorAll("tr");
          if (rows) {
            selectedPlugins = [];
            rows.forEach(function (row) {
              var input = row.querySelector("input");
              if (input.checked === true) {
                var pluginName = input.name.split(".")[1];
                selectedPlugins.push(pluginName);
              } else {
                row.remove();
              }
            });
          }
        }
      }

      clearOldResults();
      var rows = pluginManagerAvailable({
        plugins: plugins.filter(
          (plugin) => selectedPlugins.indexOf(plugin.name) === -1,
        ),
        admin,
        includeHealthScores,
      });

      tbody.insertAdjacentHTML("beforeend", rows);

      updateInstallButtonState();
    },
  );
}

var handleFilter = function (e) {
  applyFilter(e.target.value);
};

var debouncedFilter = debounce(handleFilter, 150);

document.addEventListener("DOMContentLoaded", function () {
  var filterInput = document.getElementById("filter-box");
  filterInput.addEventListener("input", function (e) {
    debouncedFilter(e);
    filterInput.parentElement.classList.add("jenkins-search--loading");
  });

  applyFilter(filterInput.value);

  setTimeout(function () {
    layoutUpdateCallback.call();
  }, 350);

  // Show update center error if element exists
  const updateCenterError = document.querySelector("#update-center-error");
  if (updateCenterError) {
    notificationBar.show(
      updateCenterError.content.textContent,
      notificationBar.ERROR,
    );
  }
});

function updateInstallButtonState() {
  // Enable/disable the 'Install' button depending on if any plugins are checked
  const anyCheckboxesSelected = () => {
    return (
      document.querySelectorAll("input[type='checkbox']:checked").length > 0
    );
  };
  const installButton = document.querySelector("#button-install");
  const installAfterRestartButton = document.querySelector(
    "#button-install-after-restart",
  );
  if (!anyCheckboxesSelected()) {
    installButton.disabled = true;
    installAfterRestartButton.disabled = true;
  }
  const checkboxes = document.querySelectorAll("input[type='checkbox']");
  checkboxes.forEach((checkbox) => {
    checkbox.addEventListener("click", () => {
      setTimeout(() => {
        installButton.disabled = !anyCheckboxesSelected();
        installAfterRestartButton.disabled = !anyCheckboxesSelected();
      });
    });
  });
}
