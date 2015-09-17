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
    .withExternalModuleMapping('moment', 'momentjs:momentjs2')
    .inDir('src/main/webapp/bundles');

//
// Create the Backward Compatibility module to globalize some modularized functions
// that existing non-modularized code will expect to be in the global scope, and
// expect them to be there immediately (i.e. no async waiting for other modules to load).
//
builder.bundle('src/main/js/jenkins-backcompat.js')
    .inDir('src/main/webapp/bundles');

// ideally we don't want to generate a file into src/ tree but maven-war-plugin only supports
// one warSourceDirectory
