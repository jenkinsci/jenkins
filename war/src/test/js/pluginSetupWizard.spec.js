import jsTest from '@jenkins-cd/js-test';

var debug = false;

// Registers needed handlebars helpers that are loaded through webpack
function registerHandlebars() {
    // eslint-disable-next-line no-undef
    const Handlebars = require('handlebars').default;
    // eslint-disable-next-line no-undef
    Handlebars.registerHelper('id', require('../../main/js/handlebars-helpers/id').default)
}

var getJQuery = function() {
    // eslint-disable-next-line no-undef
    var $ = require('jquery');
    $.fx.off = true;
    return $;
};

var getJenkins = function() {
    // eslint-disable-next-line no-undef
    return require('../../main/js/util/jenkins').default;
}

var getSetupWizardGui = function() {
    // eslint-disable-next-line no-undef
    return require('../../main/js/pluginSetupWizardGui').default;
}

/* eslint-disable-next-line */
global.defaultUpdateSiteId = 'default';

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

        var response = responseMappings[call.url] || responseMappings[`/jenkins${call.url}`];
        if (!response) {
            response = defaultMappings[call.url] || defaultMappings[`/jenkins${call.url}`];
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
var test = function(testCallback, { ajaxMappings, $, $body }) {
    jsTest.onPage(function() {
        // Respond to status request
        $.ajax = ajaxMocks(ajaxMappings);

        // load the module
        var pluginSetupWizard = getSetupWizardGui();

        // exported init
        pluginSetupWizard.init($body);

        testCallback($, pluginSetupWizard);
    });
};

// helper to validate the appropriate plugins were installed
var validatePlugins = function(plugins, complete) {
    var jenkins = getJenkins();

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
    let $;
    let $body;

    beforeEach(() => {
        registerHandlebars();

        // Create a new <body> tag for every test
        $ = getJQuery();
        $body = $('<body>');
    });

    afterEach(() => {
        $body = null;
        $ = null;

        // Reset changes made to modules, such as the 'util/jenkins' module.
        jest.resetModules();
    });

    it("wizard shows", function (done) {
        test(function() {
            // Make sure the dialog was shown
            var $wizard = $body.find('.plugin-setup-wizard');
            expect($wizard.length).toBe(1);

            done();
        }, { $, $body });
    });

    it("offline shows", function (done) {
        jsTest.onPage(function() {
            // deps
            const jenkins = getJenkins();

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
                const pluginSetupWizard = getSetupWizardGui();

                // exported init
                pluginSetupWizard.init($body);

                expect($body.find('.welcome-panel h1').text()).toBe('Offline');

                done();
            } finally {
                jenkins.get = get;
            }
        });
    });

    it("install defaults", function (done) {
        test(function() {
            // Make sure the dialog was shown
            var wizard = $body.find('.plugin-setup-wizard');
            expect(wizard.length).toBe(1);

            var goButton = $body.find('.install-recommended');
            expect(goButton.length).toBe(1);

            // validate a call to installPlugins with our defaults
            validatePlugins(['subversion'], function() {
                done();
            });

            goButton.click();
        }, { $, $body });
    });



    it("install custom", function (done) {
        function doit($body, selector, trigger) {
            var $el = $body.find(selector);
            if($el.length !== 1) {
                console.log('Not found! ' + selector);
                    console.log(new Error().stack);
            }
            if(trigger === 'check') {
                $el.prop('checked', true);
                trigger = 'change';
            }
            $el.trigger(trigger);
        }

        test(function() {
            $body.find('.install-custom').click();

            // validate a call to installPlugins with our defaults
            validatePlugins(['junit','mailer'], function() {
                // install a specific, other 'set' of plugins
                $body.find('input[name=searchbox]').val('junit');

                done();
            });

            doit($body, 'input[name=searchbox]', 'blur');

            doit($body, '.plugin-select-none', 'click');

            doit($body, 'input[name="junit"]', 'check');
            doit($body, 'input[name="mailer"]', 'check');

            doit($body, '.install-selected', 'click');
        }, { $, $body });
    });

    it("restart required", function (done) {
        var ajaxMappings = {
            '/jenkins/setupWizard/restartStatus': {
                status: 'ok',
                data: {
                    restartRequired: true,
                    restartSupported: true,
                }
            },
            '/jenkins/updateCenter/installStatus': {
                status: 'ok',
                data: {
                    state: 'INITIAL_SETUP_COMPLETED',
                    jobs: [],
                }
            },
        };

        test(function() {
            expect($body.find('.install-done').length).toBe(0);
            expect($body.find('.install-done-restart').length).toBe(1);
            done();
        }, { ajaxMappings, $, $body });
    });

    it("restart required not supported", function (done) {
        var ajaxMappings = {
            '/jenkins/setupWizard/restartStatus': {
                status: 'ok',
                data: {
                    restartRequired: true,
                    restartSupported: false,
                }
            },
            '/jenkins/updateCenter/installStatus': {
                status: 'ok',
                data: {
                    state: 'INITIAL_SETUP_COMPLETED',
                    jobs: [],
                }
            },
        };
        test(function() {
            expect($body.find('.install-done').length).toBe(0);
            expect($body.find('.install-done-restart').length).toBe(0);
            done();
        }, { ajaxMappings, $, $body });
    });

    it("restart not required", function (done) {
        var ajaxMappings = {
            '/jenkins/setupWizard/restartStatus': {
                status: 'ok',
                data: {
                    restartRequired: false,
                    restartSupported: false,
                }
            },
            '/jenkins/updateCenter/installStatus': {
                status: 'ok',
                data: {
                    state: 'INITIAL_SETUP_COMPLETED',
                    jobs: [],
                }
            },
        };
        test(function() {
            expect($body.find('.install-done').length).toBe(1);
            expect($body.find('.install-done-restart').length).toBe(0);
            done();
        }, { ajaxMappings, $, $body });
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
        test(function() {
            expect($body.find('.modal-title').text()).toBe('Resume Installation');
            expect($body.find('*[data-name="junit"]').is('.success')).toBe(true);
            done();
        }, { ajaxMappings, $, $body });
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
        test(function() {
            expect($body.find('.error-container h1').html()).toBe('Error');
            done();
        }, { ajaxMappings, $, $body });
    });

    it("restart required", function (done) {
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
                    state: 'NEW',
                    jobs: []
                }
            },
            {
                status: 'ok',
                data: {
                    state: 'INITIAL_PLUGINS_INSTALLING',
                    jobs: [
                      {
                          name: 'subversion',
                          type: 'InstallJob',
                          installStatus: 'Success',
                          requiresRestart: 'true' // a string...
                      }
                  ]
                }
            }
            ])
        };
        test(function() {
            var goButton = $body.find('.install-recommended');
            expect(goButton.length).toBe(1);

            // validate a call to installPlugins with our defaults
            setTimeout(function() {
                expect($body.find('.install-done').length).toBe(0);
                expect($body.find('.installing-panel').length).toBe(1)

                done();
            }, 1);

            goButton.click();
        }, { ajaxMappings, $, $body });
    });

    describe('running extension callbacks', function () {
        beforeEach(() => {
            global.setupWizardExtensions = [];
            global.onSetupWizardInitialized = function(extension) {
                setupWizardExtensions.push(extension);
            };

        });

        afterEach(() => {
            delete global.setupWizardExtensions;
            delete global.onSetupWizardInitialized;
        });

        it ('yields a jenkins object with the initHandlebars method', function(done) {
            jsTest.onPage(function() {

                $.ajax = ajaxMocks();

                onSetupWizardInitialized(wizard => {
                    const { jenkins } = wizard;
                    expect(jenkins).toBe(getJenkins());

                    // Test that the initHandlebars method returns a Handlebars instance
                    const handlebars = jenkins.initHandlebars();
                    expect(handlebars.registerHelper).toBeInstanceOf(Function)

                    done();
                })

                const pluginSetupWizard = getSetupWizardGui();
                pluginSetupWizard.init($body);
            });
        });
    });
});
