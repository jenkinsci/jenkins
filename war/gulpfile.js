//
// See https://github.com/tfennelly/jenkins-js-builder
//
var builder = require('jenkins-js-builder');

//
// Bundle the page init script.
// See https://github.com/jenkinsci/js-builder#bundling
//
builder.bundle('src/main/js/page-init.js')
    .withExternalModuleMapping('jquery-detached', 'core-assets/jquery-detached:jquery2')
    .inDir('src/main/webapp/jsbundles');

//
// Bundle the Install Wizard.
// See https://github.com/jenkinsci/js-builder#bundling
//
builder.bundle('src/main/js/pluginSetupWizard.js')
    .withExternalModuleMapping('jquery-detached', 'core-assets/jquery-detached:jquery2')
    .withExternalModuleMapping('bootstrap', 'core-assets/bootstrap:bootstrap3', {addDefaultCSS: true})
    .withExternalModuleMapping('handlebars', 'core-assets/handlebars:handlebars3')
    .less('src/main/less/pluginSetupWizard.less')
    .inDir('src/main/webapp/jsbundles');

//
// Bundle the Config Tab Bar.
// See https://github.com/jenkinsci/js-builder#bundling
//
builder.bundle('src/main/js/config-tabbar.js')
    .withExternalModuleMapping('jquery-detached', 'core-assets/jquery-detached:jquery2')
    .less('src/main/js/config-tabbar.less')
    .inDir('src/main/webapp/jsbundles');

//
// Bundle the Config Scrollspy.
// See https://github.com/jenkinsci/js-builder#bundling
//
builder.bundle('src/main/js/config-scrollspy.js')
    .withExternalModuleMapping('jquery-detached', 'core-assets/jquery-detached:jquery2')
    .less('src/main/js/config-scrollspy.less')
    .inDir('src/main/webapp/jsbundles');
