var builder = require('jenkins-js-builder');

//
// Use the predefined tasks from jenkins-js-builder.
//
builder.defineTasks(['test', 'bundle', 'rebundle']);

//
// Need to override the default src locations.
//
builder.src('./src/main/js');
builder.tests('./src/test/js');

//
// Create the jenkins.js JavaScript bundle.
//
builder.bundle('src/main/js/jenkins.js')
    .withExternalModuleMapping('jquery-detached-2.1.4', 'jquery-detached:jquery2')
    .withExternalModuleMapping('bootstrap-detached-3.3', 'bootstrap:bootstrap3')
    .withExternalModuleMapping('moment', 'momentjs:momentjs3')
    .inDir('src/main/webapp/bundles');