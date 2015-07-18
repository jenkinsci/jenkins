var builder = require('jenkins-js-builder');

//
// Use the predefined tasks from jenkins-js-builder.
//
builder.defineTasks(['test']);

//
// Need to override the default src locations.
//
builder.src('./src/main/js');
builder.tests('./src/test/js');
