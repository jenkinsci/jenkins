function showhideCategories(hdr,on) {
  var table = hdr.parentNode.parentNode.parentNode,
      newDisplay = on ? '' : 'none',
      nameList = new Array(), name;
  for (var i = 1; i < table.rows.length; i++) {
    if (on || table.rows[i].cells.length == 1)
      table.rows[i].style.display = newDisplay;
     else {
      // Hide duplicate rows for a plugin when not viewing by-category
      name = table.rows[i].cells[1].getAttribute('data');
      if (nameList[name] == 1) table.rows[i].style.display = 'none';
      nameList[name] = 1;
    }
  }
}
function showhideCategory(col) {
  var row = col.parentNode.nextSibling;
  var newDisplay = row && row.style.display == 'none' ? '' : 'none';
  for (; row && row.cells.length > 1; row = row.nextSibling)
    row.style.display = newDisplay;
}

Behaviour.specify("#filter-box", '_table', 0, function(e) {
      function applyFilter() {
          var filter = e.value.toLowerCase();
          ["TR.plugin","TR.plugin-category"].each(function(clz) {
            var encountered = {};
            var items = document.getElementsBySelector(clz);
            for (var i=0; i<items.length; i++) {
                var visible = (filter=="" || items[i].innerHTML.toLowerCase().indexOf(filter)>=0);
                var name = items[i].getAttribute("name");
                if (visible && name != null) {
                    if (encountered[name]) {
                        visible = false;
                    }
                    encountered[name] = true;
                }
                items[i].style.display = (visible ? "" : "none");
            }
          });

          layoutUpdateCallback.call();
      }

      e.onkeyup = applyFilter;
});

/**
 * Code for handling the enable/disable behavior based on plugin
 * dependencies and dependants.
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
        
        function markAllDependantsDisabled(pluginTR) {
            var jenkinsPluginMetadata = pluginTR.jenkinsPluginMetadata;
            var dependantIds = jenkinsPluginMetadata.dependantIds;
            
            if (dependantIds) {
                // If the only dependant is jenkins-core (it's a bundle plugin), then lets
                // treat it like all its dependants are disabled. We're really only interested in
                // dependant plugins in this case.
                // Note: This does not cover "implied" dependencies ala detached plugins. See https://goo.gl/lQHrUh
                if (dependantIds.length === 1 && dependantIds[0] === 'jenkins-core') {
                    pluginTR.addClassName('all-dependants-disabled');
                    return;
                }

                for (var i = 0; i < dependantIds.length; i++) {
                    var dependantId = dependantIds[i];

                    if (dependantId === 'jenkins-core') {
                        // Jenkins core is always enabled. So, make sure it's not possible to disable/uninstall
                        // any plugins that it "depends" on. (we sill have bundled plugins)
                        pluginTR.removeClassName('all-dependants-disabled');
                        return;
                    }
                    
                    // The dependant is a plugin....
                    var dependantPluginTr = getPluginTR(dependantId);
                    if (dependantPluginTr && dependantPluginTr.jenkinsPluginMetadata.enableInput.checked) {
                        // One of the plugins that depend on this plugin, is marked as enabled.
                        pluginTR.removeClassName('all-dependants-disabled');
                        return;
                    }
                }
            }
            
            pluginTR.addClassName('all-dependants-disabled');
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
                markAllDependantsDisabled(pluginTRs[i]);
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
                    var depPluginMetadata = depPluginTR.jenkinsPluginMetadata;
                    if (depPluginMetadata.enableInput.checked) {
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
            } if (pluginTR.hasClassName('has-dependants')) {
                if (!pluginTR.hasClassName('all-dependants-disabled')) {
                    var dependantIds = pluginMetadata.dependantIds;
                    
                    // If the only dependant is jenkins-core (it's a bundle plugin), then lets
                    // treat it like all its dependants are disabled. We're really only interested in
                    // dependant plugins in this case.
                    // Note: This does not cover "implied" dependencies ala detached plugins. See https://goo.gl/lQHrUh
                    if (dependantIds.length === 1 && dependantIds[0] === 'jenkins-core') {
                        pluginTR.addClassName('all-dependants-disabled');
                        return false;
                    }
                    
                    var dependantsDiv = pluginMetadata.dependantsDiv;
                    var dependantSpans = pluginMetadata.dependants;

                    infoContainer.update('<div class="title">' + i18n('cannot-disable') + '</div><div class="subtitle">' + i18n('enabled-dependants') + '.</div>');
                    
                    // Go through each dependant <span> element. Show the spans where the dependant is
                    // enabled. Hide the others. 
                    for (var i = 0; i < dependantSpans.length; i++) {
                        var dependantSpan = dependantSpans[i];
                        var dependantId = dependantSpan.getAttribute('data-plugin-id');
                        
                        if (dependantId === 'jenkins-core') {
                            // show the span
                            dependantSpan.setStyle({display: 'inline-block'});
                        } else {
                            var depPluginTR = getPluginTR(dependantId);
                            var depPluginMetadata = depPluginTR.jenkinsPluginMetadata;
                            if (depPluginMetadata.enableInput.checked) {
                                // It's enabled ... show the span
                                dependantSpan.setStyle({display: 'inline-block'});
                            } else {
                                // It's disabled ... hide the span
                                dependantSpan.setStyle({display: 'none'});
                            }
                        }
                    }
                    
                    dependantsDiv.setStyle({display: 'inherit'});
                    infoContainer.appendChild(dependantsDiv);

                    return true;
                }
            }
            
            return false;
        }

        function populateUninstallInfo(pluginTR, infoContainer) {
            // Remove all existing class info
            infoContainer.removeAttribute('class');
            infoContainer.addClassName('uninstall-state-info');

            if (pluginTR.hasClassName('has-dependants')) {
                var pluginMetadata = pluginTR.jenkinsPluginMetadata;
                var dependantsDiv = pluginMetadata.dependantsDiv;
                var dependantSpans = pluginMetadata.dependants;

                infoContainer.update('<div class="title">' + i18n('cannot-uninstall') + '</div><div class="subtitle">' + i18n('installed-dependants') + '.</div>');
                
                // Go through each dependant <span> element. Show them all. 
                for (var i = 0; i < dependantSpans.length; i++) {
                    var dependantSpan = dependantSpans[i];
                    dependantSpan.setStyle({display: 'inline-block'});
                }
                
                dependantsDiv.setStyle({display: 'inherit'});
                infoContainer.appendChild(dependantsDiv);
                
                return true;
            }
            
            return false;
        }

        function initPluginRowHandling(pluginTR) {
            var enableInput = select('.enable input', pluginTR);
            var dependenciesDiv = select('.dependency-list', pluginTR);
            var dependantsDiv = select('.dependant-list', pluginTR);
            var enableTD = select('td.enable', pluginTR);
            var uninstallTD = select('td.uninstall', pluginTR);
            
            pluginTR.jenkinsPluginMetadata = {
                enableInput: enableInput,
                dependenciesDiv: dependenciesDiv,
                dependantsDiv: dependantsDiv
            };

            if (dependenciesDiv) {
                pluginTR.jenkinsPluginMetadata.dependencies = selectAll('span', dependenciesDiv);
                pluginTR.jenkinsPluginMetadata.dependencyIds = processSpanSet(pluginTR.jenkinsPluginMetadata.dependencies);
            }
            if (dependantsDiv) {
                pluginTR.jenkinsPluginMetadata.dependants = selectAll('span', dependantsDiv);
                pluginTR.jenkinsPluginMetadata.dependantIds = processSpanSet(pluginTR.jenkinsPluginMetadata.dependants);
            }
            
            // Setup event handlers...
            
            // Toggling of the enable/disable checkbox requires a check and possible
            // change of visibility on the same checkbox on other plugins.
            Element.observe(enableInput, 'click', function() {
                setEnableWidgetStates();
            });
            
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

            // Handle mouse in/out of the uninstall cell (right most cell).
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

        for (var i = 0; i < pluginTRs.length; i++) {
            initPluginRowHandling(pluginTRs[i]);
        }
        
        setEnableWidgetStates();
    });
}());