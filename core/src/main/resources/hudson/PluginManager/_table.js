function checkPluginsWithoutWarnings() {
    var inputs = document.getElementsByTagName('input');
    for(var i = 0; i < inputs.length; i++) {
        var candidate = inputs[i];
        if(candidate.type === "checkbox") {
            candidate.checked = candidate.dataset.compatWarning === 'false';
        }
    }
}

Behaviour.specify("#filter-box", '_table', 0, function(e) {
    function applyFilter() {
        var filter = e.value.toLowerCase().trim();
        var filterParts = filter.split(/ +/).filter (function(word) { return word.length > 0; });
        var items = document.getElementsBySelector("TR.plugin");
        var anyVisible = false;
        for (var i=0; i<items.length; i++) {
            if ((filterParts.length < 1 || filter.length < 2) && items[i].hasClassName("hidden-by-default")) {
                items[i].addClassName("hidden");
                continue;
            }
            var makeVisible = true;

            var content = items[i].innerHTML.toLowerCase();
            for (var j = 0; j < filterParts.length; j++) {
                var part = filterParts[j];
                if (content.indexOf(part) < 0) {
                    makeVisible = false;
                    break;
                }
            }
            if (makeVisible) {
                items[i].removeClassName("hidden");
                anyVisible = true;
            } else {
                items[i].addClassName("hidden");
            }
        }
        var instructions = document.getElementById("hidden-by-default-instructions")
        if (instructions) {
            instructions.style.display = anyVisible ? 'none' : '';
        }

        layoutUpdateCallback.call();
    }
    e.onkeyup = applyFilter;

    (function() {
        var instructionsTd = document.getElementById("hidden-by-default-instructions-td");
        if (instructionsTd) { // only on Available tab
            instructionsTd.innerText = instructionsTd.getAttribute("data-loaded-text");
        }
        applyFilter();
    }());
});

/**
 * Code for handling the enable/disable behavior based on plugin
 * dependencies and dependents.
 */
(function(){
    function selectAll(selector, element) {
        if (element) {
            return $(element).select(selector);
        } else {
            return Element.select(undefined, selector);
        }
    }
    function select(selector, element) {
        var elementsBySelector = selectAll(selector, element);
        if (elementsBySelector.length > 0) {
            return  elementsBySelector[0];
        } else {
            return undefined;
        }
    }

    /**
     * Wait for document onload.
     */    
    Element.observe(window, "load", function() {        
        var pluginsTable = select('#plugins');
        var pluginTRs = selectAll('.plugin', pluginsTable);
        
        if (!pluginTRs) {
            return;
        }

        var pluginI18n = select('.plugins.i18n');
        function i18n(messageId) {
            return pluginI18n.getAttribute('data-' + messageId);
        }
        
        // Create a map of the plugin rows, making it easy to index them.
        var plugins = {};
        for (var i = 0; i < pluginTRs.length; i++) {
            var pluginTR = pluginTRs[i];
            var pluginId = pluginTR.getAttribute('data-plugin-id');
            
            plugins[pluginId] = pluginTR;
        }
        
        function getPluginTR(pluginId) {
            return plugins[pluginId];
        }
        function getPluginName(pluginId) {
            var pluginTR = getPluginTR(pluginId);
            if (pluginTR) {
                return pluginTR.getAttribute('data-plugin-name');
            } else {
                return pluginId;
            }
        }
        
        function processSpanSet(spans) {
            var ids = [];
            for (var i = 0; i < spans.length; i++) {
                var span = spans[i];
                var pluginId = span.getAttribute('data-plugin-id');
                var pluginName = getPluginName(pluginId);
                
                span.update(pluginName);
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
                if (dependentIds.length === 1 && dependentIds[0] === 'jenkins-core') {
                    pluginTR.addClassName('all-dependents-disabled');
                    return;
                }

                for (var i = 0; i < dependentIds.length; i++) {
                    var dependentId = dependentIds[i];

                    if (dependentId === 'jenkins-core') {
                        // Jenkins core is always enabled. So, make sure it's not possible to disable/uninstall
                        // any plugins that it "depends" on. (we sill have bundled plugins)
                        pluginTR.removeClassName('all-dependents-disabled');
                        return;
                    }
                    
                    // The dependent is a plugin....
                    var dependentPluginTr = getPluginTR(dependentId);
                    if (dependentPluginTr && dependentPluginTr.jenkinsPluginMetadata.enableInput.checked) {
                        // One of the plugins that depend on this plugin, is marked as enabled.
                        pluginTR.removeClassName('all-dependents-disabled');
                        return;
                    }
                }
            }
            
            pluginTR.addClassName('all-dependents-disabled');
        }

        function markHasDisabledDependencies(pluginTR) {
            var jenkinsPluginMetadata = pluginTR.jenkinsPluginMetadata;
            var dependencyIds = jenkinsPluginMetadata.dependencyIds;
            
            if (dependencyIds) {
                for (var i = 0; i < dependencyIds.length; i++) {
                    var dependencyPluginTr = getPluginTR(dependencyIds[i]);
                    if (dependencyPluginTr && !dependencyPluginTr.jenkinsPluginMetadata.enableInput.checked) {
                        // One of the plugins that this plugin depend on, is marked as disabled.
                        pluginTR.addClassName('has-disabled-dependency');
                        return;
                    }
                }
            }
            
            pluginTR.removeClassName('has-disabled-dependency');
        }
        
        function setEnableWidgetStates() {
            for (var i = 0; i < pluginTRs.length; i++) {
                var pluginMetadata = pluginTRs[i].jenkinsPluginMetadata;
                if (pluginTRs[i].hasClassName('has-dependents-but-disabled')) {
                    if (pluginMetadata.enableInput.checked) {
                            pluginTRs[i].removeClassName('has-dependents-but-disabled');
                        }
                }
                markAllDependentsDisabled(pluginTRs[i]);
                markHasDisabledDependencies(pluginTRs[i]);
            }
        }
        
        function addDependencyInfoRow(pluginTR, infoTR) {
            infoTR.addClassName('plugin-dependency-info');
            pluginTR.insert({
                after: infoTR
            });
        }
        function removeDependencyInfoRow(pluginTR) {
            var nextRows = pluginTR.nextSiblings();
            if (nextRows && nextRows.length > 0) {
                var nextRow = nextRows[0];
                if (nextRow.hasClassName('plugin-dependency-info')) {
                    nextRow.remove();
                }
            }
        }

        function populateEnableDisableInfo(pluginTR, infoContainer) {    
            var pluginMetadata = pluginTR.jenkinsPluginMetadata;

            // Remove all existing class info
            infoContainer.removeAttribute('class');
            infoContainer.addClassName('enable-state-info');

            if (pluginTR.hasClassName('has-disabled-dependency')) {
                var dependenciesDiv = pluginMetadata.dependenciesDiv;
                var dependencySpans = pluginMetadata.dependencies;

                infoContainer.update('<div class="title">' + i18n('cannot-enable') + '</div><div class="subtitle">' + i18n('disabled-dependencies') + '.</div>');
                
                // Go through each dependency <span> element. Show the spans where the dependency is
                // disabled. Hide the others. 
                for (var i = 0; i < dependencySpans.length; i++) {
                    var dependencySpan = dependencySpans[i];
                    var pluginId = dependencySpan.getAttribute('data-plugin-id');
                    var depPluginTR = getPluginTR(pluginId);
                    var enabled = false;
                    if (depPluginTR) {
                        var depPluginMetadata = depPluginTR.jenkinsPluginMetadata;
                        enabled = depPluginMetadata.enableInput.checked;
                    }
                    if (enabled) {
                        // It's enabled ... hide the span
                        dependencySpan.setStyle({display: 'none'});
                    } else {
                        // It's disabled ... show the span
                        dependencySpan.setStyle({display: 'inline-block'});
                    }
                }
                
                dependenciesDiv.setStyle({display: 'inherit'});
                infoContainer.appendChild(dependenciesDiv);
                
                return true;
            } if (pluginTR.hasClassName('has-dependents')) {
                if (!pluginTR.hasClassName('all-dependents-disabled')) {
                    var dependentIds = pluginMetadata.dependentIds;
                    
                    // If the only dependent is jenkins-core (it's a bundle plugin), then lets
                    // treat it like all its dependents are disabled. We're really only interested in
                    // dependent plugins in this case.
                    // Note: This does not cover "implied" dependencies ala detached plugins. See https://goo.gl/lQHrUh
                    if (dependentIds.length === 1 && dependentIds[0] === 'jenkins-core') {
                        pluginTR.addClassName('all-dependents-disabled');
                        return false;
                    }
                    
                    var dependentsDiv = pluginMetadata.dependentsDiv;
                    var dependentSpans = pluginMetadata.dependents;

                    infoContainer.update('<div class="title">' + i18n('cannot-disable') + '</div><div class="subtitle">' + i18n('enabled-dependents') + '.</div>');
                    
                    // Go through each dependent <span> element. Show the spans where the dependent is
                    // enabled. Hide the others. 
                    for (var i = 0; i < dependentSpans.length; i++) {
                        var dependentSpan = dependentSpans[i];
                        var dependentId = dependentSpan.getAttribute('data-plugin-id');
                        
                        if (dependentId === 'jenkins-core') {
                            // show the span
                            dependentSpan.setStyle({display: 'inline-block'});
                        } else {
                            var depPluginTR = getPluginTR(dependentId);
                            var depPluginMetadata = depPluginTR.jenkinsPluginMetadata;
                            if (depPluginMetadata.enableInput.checked) {
                                // It's enabled ... show the span
                                dependentSpan.setStyle({display: 'inline-block'});
                            } else {
                                // It's disabled ... hide the span
                                dependentSpan.setStyle({display: 'none'});
                            }
                        }
                    }
                    
                    dependentsDiv.setStyle({display: 'inherit'});
                    infoContainer.appendChild(dependentsDiv);

                    return true;
                }
            }

            if (pluginTR.hasClassName('possibly-has-implied-dependents')) {
                infoContainer.update('<div class="title">' + i18n('detached-disable') + '</div><div class="subtitle">' + i18n('detached-possible-dependents') + '</div>');
                return true;
            }
            
            return false;
        }

        function populateUninstallInfo(pluginTR, infoContainer) {
            // Remove all existing class info
            infoContainer.removeAttribute('class');
            infoContainer.addClassName('uninstall-state-info');

            if (pluginTR.hasClassName('has-dependents')) {
                var pluginMetadata = pluginTR.jenkinsPluginMetadata;
                var dependentsDiv = pluginMetadata.dependentsDiv;
                var dependentSpans = pluginMetadata.dependents;

                infoContainer.update('<div class="title">' + i18n('cannot-uninstall') + '</div><div class="subtitle">' + i18n('installed-dependents') + '.</div>');
                
                // Go through each dependent <span> element. Show them all. 
                for (var i = 0; i < dependentSpans.length; i++) {
                    var dependentSpan = dependentSpans[i];
                    dependentSpan.setStyle({display: 'inline-block'});
                }
                
                dependentsDiv.setStyle({display: 'inherit'});
                infoContainer.appendChild(dependentsDiv);
                
                return true;
            }
            
            if (pluginTR.hasClassName('possibly-has-implied-dependents')) {
                infoContainer.update('<div class="title">' + i18n('detached-uninstall') + '</div><div class="subtitle">' + i18n('detached-possible-dependents') + '</div>');
                return true;
            }

            return false;
        }

        function initPluginRowHandling(pluginTR) {
            var enableInput = select('.enable input', pluginTR);
            var dependenciesDiv = select('.dependency-list', pluginTR);
            var dependentsDiv = select('.dependent-list', pluginTR);
            var enableTD = select('td.enable', pluginTR);
            var uninstallTD = select('td.uninstall', pluginTR);
            
            pluginTR.jenkinsPluginMetadata = {
                enableInput: enableInput,
                dependenciesDiv: dependenciesDiv,
                dependentsDiv: dependentsDiv
            };

            if (dependenciesDiv) {
                pluginTR.jenkinsPluginMetadata.dependencies = selectAll('span', dependenciesDiv);
                pluginTR.jenkinsPluginMetadata.dependencyIds = processSpanSet(pluginTR.jenkinsPluginMetadata.dependencies);
            }
            if (dependentsDiv) {
                pluginTR.jenkinsPluginMetadata.dependents = selectAll('span', dependentsDiv);
                pluginTR.jenkinsPluginMetadata.dependentIds = processSpanSet(pluginTR.jenkinsPluginMetadata.dependents);
            }
            
            // Setup event handlers...
            if (enableInput) {
                // Toggling of the enable/disable checkbox requires a check and possible
                // change of visibility on the same checkbox on other plugins.
                Element.observe(enableInput, 'click', function() {
                    setEnableWidgetStates();
                });
            }
            
            // 
            var infoTR = document.createElement("tr");
            var infoTD = document.createElement("td");
            var infoDiv = document.createElement("div");
            infoTR.appendChild(infoTD)
            infoTD.appendChild(infoDiv)
            infoTD.setAttribute('colspan', '6'); // This is the cell that all info will be added to.
            infoDiv.setStyle({display: 'inherit'});

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
                Element.observe(enableTD, 'mouseenter', function() {
                    showInfoTimeout = setTimeout(function() {
                        showInfoTimeout = undefined;
                        infoDiv.update('');
                        if (populateEnableDisableInfo(pluginTR, infoDiv)) {
                            addDependencyInfoRow(pluginTR, infoTR);
                        }
                    }, 1000);
                });
                Element.observe(enableTD, 'mouseleave', function() {
                    clearShowInfoTimeout();
                    removeDependencyInfoRow(pluginTR);
                });
            }

            // Handle mouse in/out of the uninstall cell (right most cell).
            if (uninstallTD) {
                Element.observe(uninstallTD, 'mouseenter', function() {
                    showInfoTimeout = setTimeout(function() {
                        showInfoTimeout = undefined;
                        infoDiv.update('');
                        if (populateUninstallInfo(pluginTR, infoDiv)) {
                            addDependencyInfoRow(pluginTR, infoTR);
                        }
                    }, 1000);
                });
                Element.observe(uninstallTD, 'mouseleave', function() {
                    clearShowInfoTimeout();
                    removeDependencyInfoRow(pluginTR);
                });
            }
        }

        for (var i = 0; i < pluginTRs.length; i++) {
            initPluginRowHandling(pluginTRs[i]);
        }
        
        setEnableWidgetStates();
    });
}());

Element.observe(window, "load", function() {
    document.getElementById('filter-box').focus();
});