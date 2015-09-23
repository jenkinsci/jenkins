/**
 * Jenkins first-run install wizard
 */

// Require modules here, make sure they get browserify'd/bundled
var jquery = require('jquery-detached');
var bootstrap = require('bootstrap-detached');

// Setup the dialog, exported
var createPluginSetupWizard = function() {
	// call getJQuery / getBootstrap within the main function so it will work with tests -- if getJQuery etc is called in the main 
	var $ = jquery.getJQuery();
	var $bs = bootstrap.getBootstrap();
	var wh = require('window-handle');
	var jenkins = require('./util/jenkins');

	var Handlebars = jenkins.initHandlebars();

	// Necessary handlebars helpers:
	// returns the plugin count string per category selected vs. available e.g. (5/44)
	Handlebars.registerHelper('pluginCountForCategory', function(cat) {
		var plugs = categorizedPlugins[cat];
		var tot = 0;
		var cnt = 0;
		for(var i = 0; i < plugs.length; i++) {
			var plug = plugs[i];
			if(plug.category == cat) {
				tot++;
				if(selectedPlugins.indexOf(plug.plugin.name) >= 0) {
					cnt++;
				}
			}
		}
		return '(' + cnt + '/' + tot + ')';
	});

	// returns the total plugin count string selected vs. total e.g. (5/44)
	Handlebars.registerHelper('totalPluginCount', function() {
		var tot = 0;
		var cnt = 0;
		for(var i = 0; i < installData.availablePlugins.length; i++) {
			var a = installData.availablePlugins[i];
			for(var c = 0; c < a.plugins.length; c++) {
				var plug = a.plugins[c];
				tot++;
				if(selectedPlugins.indexOf(plug.name) >= 0) {
					cnt++;
				}
			}
		}
		return '(' + cnt + '/' + tot + ')';
	});

	// determines if the provided plugin is in the list currently selected
	Handlebars.registerHelper('inSelectedPlugins', function(val, options) {
		if(selectedPlugins.indexOf(val) >= 0) {
			return options.fn();
		}
	});

	// Include handlebars templates here - explicitly require them and they'll be available by hbsfy as part of the bundle process
	var errorPanel = require('./templates/errorPanel.hbs');
	var welcomePanel = require('./templates/welcomePanel.hbs');
	var progressPanel = require('./templates/progressPanel.hbs');
	var pluginSelectionPanel = require('./templates/pluginSelectionPanel.hbs');
	var successPanel = require('./templates/successPanel.hbs');
	var offlinePanel = require('./templates/offlinePanel.hbs');
	var pluginSetupWizard = require('./templates/pluginSetupWizard.hbs');

	// state variables for plugin data, selected plugins, etc.:
	var installData;
	var categories = [];
	var selectedCategory;
	var availablePlugins = {};
	var categorizedPlugins = {};
	var recommendedPlugins = [];
	var selectedPlugins;

	// This could be an AJAX call, for now, just reading the included file
	var getInstallData = function() {
		installData = require('./recommendedPlugins.js');
		selectedPlugins = installData.defaultPlugins.slice(0); // default the set of plugins, this is just names
	};
	getInstallData();
	
	// Instantiate the wizard panel
	var $wizard = $(pluginSetupWizard());
	$wizard.appendTo('body');
	var $container = $wizard.find('.modal-content');
	
	// localized messages
	var msg = {};
	
	// call this to set the panel in the app, this performs some additional things & adds common transitions
	var setPanel = function(panel, data, oncomplete) {
		var append = function() {
			$wizard.attr('data-panel', panel.name);
			$container.append(panel($.extend({msg: msg}, data)));
			if(oncomplete) {
				oncomplete();
			}
		};
		
		var $modalBody = $container.find('.modal-body');
		if($modalBody.length > 0) {
			$modalBody.stop(true).fadeOut(250, function() {
				$container.children().remove();
				append();
			});
		}
		else {
			$container.children().remove();
			append();
		}
	};
	
	// we get a 'correlationId' when install plugins, it's stored here:
	var installId;
	
	// call this to go install the selected set of plugins
	var installPlugins = function(plugins) {
		jenkins.post('/pluginManager/installPlugins', { dynamicLoad: true, plugins: plugins }, function(data) {
			if(data.status != 'ok') {
				// error!
			}
			installId = data.data.correlationId;
			
			showInstallProgress();
		});
		setPanel(progressPanel);
	};
	
	// install the default plugins
	var installDefaultPlugins = function() {
		installPlugins(installData.defaultPlugins);
	};
	
	// Define actions
	var showInstallProgress = function() {
		setPanel(progressPanel);
		
		var updateStatus = installId ?
			function() {
				// call to the installStatus, update progress bar & plugin details; transition on complete
				jenkins.get('/updateCenter/installStatus?correlationId=' + installId, function(data) {
					var i, j;
					var complete = 0;
					var total = 0;
					var jobs = data.data;
					for(i = 0; i < jobs.length; i++) {
						j = jobs[i];
						total++;
						if(/.*Success.*/.test(j.installStatus)||/.*Fail.*/.test(j.installStatus)) {
							complete++;
						}
					}
					
					// update progress bar
					$('.progress-bar').css({width: ((100.0 * complete)/total) + '%'});
					
					// update details
					var $c = $('.install-text');
					$c.children().remove();
					for(i = 0; i < jobs.length; i++) {
						j = jobs[i];
						if(/.*Success.*/.test(j.installStatus)) {
							$c.append('<div>'+j.title+'</div>');
						}
						else if(/.*Install.*/.test(j.installStatus)) {
							$c.append('<div>'+j.title+'</div>');
						}
						else if(/.*Fail.*/.test(j.installStatus)) {
							$c.append('<div>'+j.title+' -- FAILED '+'</div>');
						}
					}
					if($c.is(':visible')) {
						$c[0].scrollTop = $c[0].scrollHeight;
					}
					
					// keep polling while install is running
					if(complete < total) {
						// wait a sec
						setTimeout(updateStatus, 250);
					}
					else {
						// mark complete
						$('.progress-bar').css({width: '100%'});
						installId = null;
						setPanel(successPanel);
					}
				});
			}
			// this might be picking up an existing installation (e.g. refreshing the page or something)
			: function() {
				jenkins.get('/updateCenter/api/json?tree=jobs[name,status[*],errorMessage]', function(data) {
					var i, j;
					var complete = 0;
					var total = 0;
					var jobs = data.jobs;
					for(i = 0; i < jobs.length; i++) {
						j = jobs[i];
						if('status' in j) {
							total++;
							if(/.*Success.*/.test(j.status.type)||/.*Fail.*/.test(j.status.type)) {
								complete++;
							}
						}
					}
					
					$('.progress-bar').css({width: ((100.0 * complete)/total) + '%'});
					
					var $c = $('.install-console');
					{
						$c = $('.install-text');
						$c.children().remove();
						for(i = 0; i < jobs.length; i++) {
							j = jobs[i];
							if('status' in j) {
								if(/.*Success.*/.test(j.status.type)) {
									$c.append('<div>'+j.name+'</div>');
								}
								else if(/.*Install.*/.test(j.status.type)) {
									$c.append('<div>'+j.name+'</div>');
								}
								else if(/.*Fail.*/.test(j.status.type)) {
									$c.append('<div>'+j.name+' -- '+j.errorMessage+'</div>');
								}
							}
						}
						if($c.is(':visible')) {
							$c[0].scrollTop = $c[0].scrollHeight;
						}
					}
					
					if(complete < total) {
						// wait a sec
						setTimeout(updateStatus, 250);
					}
					else {
						$('.progress-bar').css({width: '100%'});
						setPanel(successPanel);
					}
				});
			};
		
		// kick it off
		setTimeout(updateStatus, 250);
	};
	
	// Called to complete the installation
	var finishInstallation = function() {
		jenkins.go('/');
	};
	
	// load the plugin data, callback
	var loadPluginData = function(oncomplete) {
		jenkins.get('/updateCenter/api/json?tree=availables[*,*[*]]', function(data) {
			var a = data.availables;
			for(var i = 0; i < a.length; i++) {
				var plug = a[i];
				availablePlugins[plug.name] = plug;
			}
			oncomplete();
		});
	};
	
	// load the custom plugin panel, will result in an AJAX call to get the plugin data
	var loadCustomPluginPanel = function() {
		loadPluginData(function() {
			categories = [];
			for(var i = 0; i < installData.availablePlugins.length; i++) {
				var a = installData.availablePlugins[i];
				categories.push(a.category);
				var plugs = categorizedPlugins[a.category] = [];
				for(var c = 0; c < a.plugins.length; c++) {
					var plugInfo = a.plugins[c];
					var plug = availablePlugins[plugInfo.name];
					if(!plug) {
						plug = {
							name: plugInfo.name,
							title: plugInfo.name
						};
					}
					recommendedPlugins.push(plug.name);
					plugs.push({
						category: a.category,
						plugin: $.extend(plug, {
							usage: plugInfo.usage,
							title: plugInfo.title ? plugInfo.title : plug.title,
							excerpt: plugInfo.excerpt ? plugInfo.excerpt : plug.excerpt,
							updated: new Date(plug.buildDate)
						})
					});
				}
			}
			setPanel(pluginSelectionPanel, pluginSelectionPanelData(), function() {
				$bs('.plugin-selector .plugin-list').scrollspy({ target: '.plugin-selector .categories' });
			});
		});
	};
	
	// get plugin selection panel data object
	var pluginSelectionPanelData = function() {
		return {
			categories: categories,
			categorizedPlugins: categorizedPlugins,
			selectedPlugins: selectedPlugins
		};
	};

	// remove a plugin from the selected list
	var removePlugin = function(arr, item) {
		for (var i = arr.length; i--;) {
			if (arr[i] === item) {
				arr.splice(i, 1);
			}
		}
	};
	
	// add a plugin to the selected list
	var addPlugin = function(arr, item) {
		arr.push(item);
	};
	
	// refreshes the plugin selection panel; call this if anything changes to ensure everything is kept in sync
	var refreshPluginSelectionPanel = function() {
		var html = pluginSelectionPanel($.extend({msg: msg}, pluginSelectionPanelData()));
		
		var $upd = $(html);
		$upd.find('*[id]').each(function() {
			var $el = $(this);
			$('#'+$el.attr('id')).replaceWith($el);
		});
		if(lastSearch !== '') {
			searchForPlugins(lastSearch, false);
		}
	};
	
	// handle clicking an item in the plugin list
	$wizard.on('change', '.plugin-list input[type=checkbox]', function() {
		var $input = $(this);
		if($input.is(':checked')) {
			addPlugin(selectedPlugins, $input.attr('name'));
		}
		else {
			removePlugin(selectedPlugins, $input.attr('name'));
		}
		
		refreshPluginSelectionPanel();
	});
	
	// walk the elements and search for the text
    var walk = function(elements, element, text, xform) {
        var i, child, n= element.childNodes.length;
        for (i = 0; i<n; i++) {
            child = element.childNodes[i];
            if (child.nodeType===3 && xform(child.data).indexOf(text)!==-1) {
                elements.push(element);
                break;
            }
        }
        for (i = 0; i<n; i++) {
            child = element.childNodes[i];
            if (child.nodeType===1)
                walk(elements, child, text, xform);
        }
    };
    
    // find elements matching the given text, optionally transforming the text before match (e.g. you can .toLowerCase() it)
	var findElementsWithText = function(ancestor, text, xform) {
	    var elements= [];
	    walk(elements, ancestor, text, xform ? xform : function(d){ return d; });
	    return elements;
	};
	
	// search UI vars
	var findIndex = 0;
	var lastSearch = '';
	
	// scroll to the next match
	var scrollPlugin = function($pl, matches, term) {
		if(matches.length > 0) {
			if(lastSearch != term) {
				findIndex = 0;
			}
			else {
				findIndex = (findIndex+1) % matches.length;
			}
			var $el = $(matches[findIndex]);
			$el = $el.parents('label:first'); // scroll to the block
			if($el && $el.length > 0) {
				var pos = $pl.scrollTop() + $el.position().top;
				$pl.stop(true).animate({
					scrollTop: pos
				}, 100);
				setTimeout(function() { // wait for css transitions to finish
					var pos = $pl.scrollTop() + $el.position().top;
					$pl.stop(true).animate({
						scrollTop: pos
					}, 50);
				}, 50);
			}
		}
	};
	
	// search for given text, optionally scroll to the next match, set classes on appropriate elements from the search match
	var searchForPlugins = function(text, scroll) {
		var $pl = $('.plugin-list');
		var $containers = $pl.find('label');
		
		// must always do this, as it's called after refreshing the panel (e.g. check/uncheck plugs)
		$containers.removeClass('match');
		$pl.find('h2').removeClass('match');
		
		if(text.length > 1) {
			if(text == 'show:selected') {
				$('.plugin-list .selected').addClass('match');
			}
			else {
				var matches = findElementsWithText($pl[0], text.toLowerCase(), function(d) { return d.toLowerCase(); });
				$(matches).parents('label').addClass('match');
				if(lastSearch != text && scroll) {
					scrollPlugin($pl, matches, text);
				}
			}
			$('.match').parent().prev('h2').addClass('match');
			$pl.addClass('searching');
		}
		else {
			findIndex = 0;
			$pl.removeClass('searching');
		}
		lastSearch = text;
	};
	
	// handle input for the search here
	$wizard.on('keyup change', '.plugin-select-controls input[name=searchbox]', function(e) {
		var val = $(this).val();
		searchForPlugins(val, true);
	});
	
	// handle clearing the search
	$wizard.on('click', '.clear-search', function() {
		$('input[name=searchbox]').val('');
		searchForPlugins('', false);
	});
	
	// toggles showing the selected items as a simple search
	var toggleSelectedSearch = function() {
		var $srch = $('input[name=searchbox]');
		var val = 'show:selected';
		if($srch.val() == val) {
			val = '';
		}
		$srch.val(val);
		searchForPlugins(val, false);
	};
	
	// handle clicking on the category
	var selectCategory = function() {
		$('input[name=searchbox]').val('');
		searchForPlugins('', false);
		var id = $(this).attr('href');
		var $el = $($(this).attr('href'));
		var $pl = $('.plugin-list');
		var top = $pl.scrollTop() + $el.position().top;
		$pl.stop(true).animate({
			scrollTop: top
		}, 250, function() {
			var top = $pl.scrollTop() + $el.position().top;
			$pl.stop(true).scrollTop(top);
		})
	};
	
	// handle show/hide details during the installation progress panel
	var toggleInstallDetails = function() {
		var $c = $('.install-console');
		if($c.is(':visible')) {
			$c.slideUp();
		}
		else {
			$c.slideDown();
		}
	};
	
	// scoped click handler, prevents default actions automatically
	var bindClickHandler = function(cls, action) {
		$wizard.on('click', cls, function(e) {
			action.apply(this, arguments);
			e.preventDefault();
		});
	};
	
	// click action mappings
	var actions = {
		'.install-recommended': installDefaultPlugins,
		'.install-custom': loadCustomPluginPanel,
		'.install-home': function() { setPanel(welcomePanel); },
		'.install-selected': function() { installPlugins(selectedPlugins); },
		'.toggle-install-details': toggleInstallDetails,
		'.install-done': finishInstallation,
		'.plugin-select-all': function() { selectedPlugins = recommendedPlugins.slice(0); refreshPluginSelectionPanel(); },
		'.plugin-select-none': function() { selectedPlugins = []; refreshPluginSelectionPanel(); },
		'.plugin-select-recommended': function() { selectedPlugins = installData.defaultPlugins.slice(0); refreshPluginSelectionPanel(); },
		'.plugin-show-selected': toggleSelectedSearch,
		'.select-category': selectCategory
	};
	for(var cls in actions) {
		bindClickHandler(cls, actions[cls]);
	}
	
	// kick off to get resource bundle
	jenkins.get('/i18n/resourceBundle?baseName=jenkins.install.pluginSetupWizard', function(res) {
		if(res.status != 'ok') {
			msg = { error: 'Unable to load localization data: ' + res.message };
			setPanel(errorPanel);
			return;
		}
		
		msg = res.data;
		
		// check for updates when first loaded...
		jenkins.get('/updateCenter/api/json?tree=jobs[name,type,status[*]]', function(data) {
			// check for install jobs
			for(var i = 0; i < data.jobs.length; i++) {
				if(data.jobs[i].type != 'ConnectionCheckJob') {
					showInstallProgress();
					return;
				}
			}
			
			// If no active install, by default, we'll show the welcome screen
			setPanel(welcomePanel);
		});
		
		// check the connectivity api
		var testConnectivity = function() {
			jenkins.get('/updateCenter/connectionStatus?siteId=default', function(response) {
				var uncheckedStatuses = ['CHECKING', 'UNCHECKED'];
				if(uncheckedStatuses.indexOf(response.data.updatesite) >= 0  || uncheckedStatuses.indexOf(response.data.internet) >= 0) {
					setTimeout(testConnectivity, 500);
				}
				else {
					if(response.status != 'ok' || response.data.updatesite != 'OK' || response.data.internet != 'OK') {
						setPanel(offlinePanel);
					}
				}
			});
		};
		testConnectivity();
	});
};

// export wizard creation method
exports.init = createPluginSetupWizard;
