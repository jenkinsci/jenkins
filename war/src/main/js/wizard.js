var jQueryUI = require('jqueryui-detached');
var $ = jQueryUI.getJQueryUI();
var browser = require('./util/browser');

var wizardTemplate = require('./templates/wizard.hbs');
var modal = $(wizardTemplate());

$('body').append(modal);

modal.dialog({
    dialogClass: "jenkins-plugin-wizard-dialog",
    modal: true,
    minWidth: 400,
    buttons: {
        "Install Recommended Plugins": function () {
            var theDialog = this;
            
            $('#jenkins-plugin-wizard .instructions').hide();
            $('#jenkins-plugin-wizard .installing').show();

            // Install the "recommended" plugins ...
            var pm = require('./pluginManager');
    
            // Install a few random "recommended" plugins. This list is yet 
            // to be drawn up.
            pm.installPlugins(['github', 'workflow-aggregator'], function() {
                $(theDialog).dialog("close");
                modal.remove();

                // Redirect the browser to the update center page so 
                // the user can follow the plugin install progress.
                // The real wizard (being worked on in parallel) has a nicer UX for this.
                browser.goTo('/updateCenter')                
            });
        }
    }
});

// Need to wrap. See https://github.com/jenkinsci/js-libs/tree/master/jquery-detached#namespacing-pitfalls
$('.jenkins-plugin-wizard-dialog').wrap('<div class="jquery-ui-1"></div>');

