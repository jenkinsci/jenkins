import debounce from 'lodash/debounce'
import requestAnimationFrame from 'raf';

import pluginManagerAvailable from './templates/plugin-manager/available.hbs'
import pluginManager from './api/pluginManager';

const filterInput = document.getElementById('filter-box');

function applyFilter(searchQuery) {
    // debounce reduces number of server side calls while typing
    pluginManager.availablePluginsSearch(searchQuery.toLowerCase().trim(), 50, function (plugins) {
		const pluginsTable = document.getElementById('plugins');
		const tbody = pluginsTable.querySelector('tbody');
		const admin = pluginsTable.dataset.hasadmin === 'true';
		let selectedPlugins = [];

		filterInput.parentElement.classList.remove("jenkins-search--loading");

        function clearOldResults() {
            if (!admin) {
                tbody.innerHTML = '';
            } else {
				const rows = tbody.querySelectorAll('tr');
				if (rows) {
                    selectedPlugins = []
                    rows.forEach(function (row) {
						const input = row.querySelector('input');
						if (input.checked === true) {
							const pluginName = input.name.split('.')[1];
							selectedPlugins.push(pluginName)
                        } else {
                            row.remove();
                        }
                    })
                }
            }
        }

        clearOldResults()
		const rows = pluginManagerAvailable({
			plugins: plugins.filter(plugin => selectedPlugins.indexOf(plugin.name) === -1),
			admin
		});

		tbody.insertAdjacentHTML('beforeend', rows);

        // @see JENKINS-64504 - Update the sticky buttons position after each search.
        requestAnimationFrame(() => {
            layoutUpdateCallback.call()
        })
    })
}

const handleFilter = function (e) {
	applyFilter(e.target.value)
};

const debouncedFilter = debounce(handleFilter, 150);

document.addEventListener("DOMContentLoaded", function () {
    filterInput.addEventListener('input', function (e) {
		debouncedFilter(e);
		filterInput.parentElement.classList.add("jenkins-search--loading");
	});

    applyFilter(filterInput.value);

    setTimeout(function () {
        layoutUpdateCallback.call();
    }, 350)
});
