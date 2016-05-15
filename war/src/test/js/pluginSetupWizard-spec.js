var jsTest = require("jenkins-js-test");
var jquery = require('jquery-detached');

var debug = false;

var getJQuery = function() {
    var $ = jquery.getJQuery();
    $.fx.off = true;
    return $;
};

// Iterates through all responses until the end and returns the last response repeatedly
var LastResponse = function(responses) {
    var counter = 0;
    this.next = function() {
        if(counter < responses.length) {
            try {
                return responses[counter];
            } finally {
                counter++;
            }
        }
        if(responses.length > 0) {
            return responses[counter-1];
        }
        return { status: 'fail' };
    };
};

// common mocks for jQuery $.ajax
var ajaxMocks = function(responseMappings) {
    var defaultMappings = {
        '/jenkins/i18n/resourceBundle?baseName=jenkins.install.pluginSetupWizard': {
            status: 'ok',
            data: {
                'installWizard_offline_title': 'Offline',
                'installWizard_error_header': 'Error',
                'installWizard_installIncomplete_title': 'Resume Installation'
            }
        },
        '/jenkins/updateCenter/incompleteInstallStatus': {
            status: 'ok',
            data: []
        },
        '/jenkins/updateCenter/installStatus': new LastResponse([{
            status: 'ok',
            data: { // first, return nothing by default, no ongoing install
            	state: 'NEW',
            	jobs: []
        	}
        },
        {
            status: 'ok',
            data: {
            	state: 'INSTALLING_PLUGINS',
            	jobs: [
	              {
	                  name: 'subversion',
	                  type: 'InstallJob',
	                  installStatus: 'Success'
	              }
	            ]
            }
        }]),
        '/jenkins/pluginManager/plugins': {
            status: 'ok',
            data: [
                {
                    name: 'junit',
                    title: 'JUnit plugin',
                    dependencies: {}
                },
                {
                    name: 'subversion',
                    title: 'Subversion plugin'
                },
                {
                    name: 'other',
                    title: 'Other thing',
                    dependencies: { 'subversion': '1' }
                },
                {
                    name: 'mailer',
                    title: 'Mailer plugin',
                    dependencies: { 'other': '1' }
                }
            ]
        },
        '/jenkins/updateCenter/connectionStatus?siteId=default': {
            status: 'ok',
            data: {
                updatesite: 'OK',
                internet: 'OK'
            }
        },
        '/jenkins/pluginManager/installPlugins': {
          status: 'ok',
          data: 'RANDOM_UUID_1234'
        },
        '/jenkins/setupWizard/platformPluginList': {
        status: 'ok',
        data: [
        {
         "category":"Stuff",
         "plugins": [
             { "name": "mailer" },
             { "name": "junit" },
             { "name": "workflow-aggregator", "added": "2.0" },
         ]
        },
        {
         "category":"Source Code Management",
         "plugins": [
             { "name": "git" },
             { "name": "subversion", "suggested": true }
         ]
        },
        {
         "category":"User Management and Security",
         "plugins": [            
             { "name": "matrix-auth" },
             { "name": "pam-auth" },
             { "name": "ldap" }
         ]
        },
        {
         "category":"Notifications and Publishing",
         "plugins": [
             { "name": "email-ext" },
             { "name": "ssh" }
         ]
        }
        ]
        }
    };

    if (!responseMappings) {
        responseMappings = {};
    }

    return function(call) {
        if(debug) {
            console.log('AJAX call: ' + call.url);
        }

        var response = responseMappings[call.url];
        if (!response) {
            response = defaultMappings[call.url];
        }
        if (!response) {
            throw 'No data mapping provided for AJAX call: ' + call.url;
        }
        if(response instanceof LastResponse) {
        response = response.next();
        }
        call.success(response);
    };
};

// call this for each test, it will provide a new wizard, jquery to the caller
var test = function(test, ajaxMappings) {
    jsTest.onPage(function() {
        // deps
        var $ = getJQuery();

        // Respond to status request
        $.ajax = ajaxMocks(ajaxMappings);

        // load the module
        var pluginSetupWizard = jsTest.requireSrcModule('pluginSetupWizardGui');

        // exported init
        pluginSetupWizard.init('body');

        test($, pluginSetupWizard);
    });
};

// helper to validate the appropriate plugins were installed
var validatePlugins = function(plugins, complete) {
    var jenkins = jsTest.requireSrcModule('util/jenkins');
    if(!jenkins.originalPost) {
    jenkins.originalPost = jenkins.post;
    }
    jenkins.post = function(url, data, cb) {
        expect(url).toBe('/pluginManager/installPlugins');
        var allMatch = true;
        for(var i = 0; i < plugins.length; i++) {
        if(data.plugins.indexOf(plugins[i]) < 0) {
            allMatch = false;
            break;
        }
        }
        if(!allMatch) {
            expect(JSON.stringify(plugins)).toBe('In: ' + JSON.stringify(data.plugins));
        }
        // return status
        cb({status:'ok',data:{correlationId:1}});
        if(complete) {
        complete();
        }
    };
};

describe("pluginSetupWizard.js", function () {
    it("wizard shows", function (done) {
        test(function($) {
            // Make sure the dialog was shown
            var $wizard = $('.plugin-setup-wizard');
            expect($wizard.size()).toBe(1);

            done();
        });
    });

    it("offline shows", function (done) {
        jsTest.onPage(function() {
            // deps
            var jenkins = jsTest.requireSrcModule('./util/jenkins');

            var $ = getJQuery();
            $.ajax = ajaxMocks();

            var get = jenkins.get;
            try {
                // Respond with failure
            jenkins.get = function(url, cb) {
                if (debug) {
                        console.log('Jenkins.GET: ' + url);
                    }

                if(url === '/updateCenter/connectionStatus?siteId=default') {
                    cb({
                        status: 'ok',
                        data: {
                            updatesite: 'ERROR',
                            internet: 'ERROR'
                        }
                    });
                }
                else {
                    get(url, cb);
                }
                };

                // load the module
                var pluginSetupWizard = jsTest.requireSrcModule('pluginSetupWizardGui');

                // exported init
                pluginSetupWizard.init('body');

                expect($('.welcome-panel h1').text()).toBe('Offline');

            done();
            } finally {
            jenkins.get = get;
            }
        });
    });

    it("install defaults", function (done) {
        test(function($) {
            // Make sure the dialog was shown
            var wizard = $('.plugin-setup-wizard');
            expect(wizard.size()).toBe(1);

            var goButton = $('.install-recommended');
            expect(goButton.size()).toBe(1);

            // validate a call to installPlugins with our defaults
            validatePlugins(['subversion'], function() {
                done();
            });

            goButton.click();
        });
    });

    var doit = function($, sel, trigger) {
    var $el = $(sel);
    if($el.length !== 1) {
        console.log('Not found! ' + sel);
            console.log(new Error().stack);
    }
    if(trigger === 'check') {
        $el.prop('checked', true);
        trigger = 'change';
    }
    $el.trigger(trigger);
    };

    it("install custom", function (done) {
        test(function($) {
            $('.install-custom').click();

            // validate a call to installPlugins with our defaults
            validatePlugins(['junit','mailer'], function() {
                // install a specific, other 'set' of plugins
                $('input[name=searchbox]').val('junit');

                done();
            });

            doit($, 'input[name=searchbox]', 'blur');

            doit($, '.plugin-select-none', 'click');

            doit($, 'input[name="junit"]', 'check');
            doit($, 'input[name="mailer"]', 'check');

            doit($, '.install-selected', 'click');
        });
    });

    it("resume install", function (done) {
        var ajaxMappings = {
        '/jenkins/updateCenter/incompleteInstallStatus': {
                status: 'ok',
                data: {
                    'junit': 'Success',
                    'subversion': 'Pending',
                    'mailer': 'Success',
                    'other': 'Failed'
                }
            }
        };
        test(function($) {
        expect($('.modal-title').text()).toBe('Resume Installation');
        expect($('*[data-name="junit"]').is('.success')).toBe(true);
        done();
        }, ajaxMappings);
    });

    it("error conditions", function (done) {
        var ajaxMappings = {
        '/jenkins/updateCenter/incompleteInstallStatus': {
                status: 'error',
                data: {
            'junit': 'Success',
            'subversion': 'Pending',
            'other': 'Failed',
            'mailer': 'Success'
                }
            }
        };
        test(function($) {
        expect($('.error-container h1').html()).toBe('Error');
        done();
        }, ajaxMappings);
    });

    it("restart required", function (done) {
        var jenkins = jsTest.requireSrcModule('util/jenkins');
        if(jenkins.originalPost) {
            jenkins.post = jenkins.originalPost;
        }

        var ajaxMappings = {
            '/jenkins/updateCenter/installStatus': new LastResponse([{
                status: 'ok',
                data: { // first, return nothing by default, no ongoing install
                    state: 'NEW',
                    jobs: []
                }
            },
            {
                status: 'ok',
                data: {
                    state: 'INSTALLING_PLUGINS',
                    jobs: [
                      {
                          name: 'subversion',
                          type: 'InstallJob',
                          installStatus: 'Success',
                          requiresRestart: 'true' // a string...
                      }
                  ]
                }
            }])
        };
        test(function($) {
            var goButton = $('.install-recommended');
            expect(goButton.size()).toBe(1);

            // validate a call to installPlugins with our defaults
        setTimeout(function() {
                expect($('.install-done').is(':visible')).toBe(false);

                expect($('.save-first-user').is(':visible')).toBe(true);

                done();
        }, 500);

            goButton.click();
        }, ajaxMappings);
    });

});