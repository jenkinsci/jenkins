//
// See https://github.com/tfennelly/jenkins-js-builder
//
var builder = require('jenkins-js-builder');

//
// Bundle the Install Wizard.
// See https://github.com/tfennelly/jenkins-js-builder#bundling
//
builder.bundle('src/main/js/pluginSetupWizard.js')
    .withExternalModuleMapping('jquery-detached', 'jquery-detached:jquery2')
    .withExternalModuleMapping('bootstrap', 'bootstrap:bootstrap3')
    .withExternalModuleMapping('handlebars', 'handlebars:handlebars3')
    .less('src/main/less/pluginSetupWizard.less')
    .inDir('src/main/webapp/jsbundles');

// everything for jslint below:
var gulp = require('gulp');
var jshint = require('gulp-jshint');
gulp.task('lint', function(){
    return gulp.src('src/main/js/**/*.js')
        .pipe(jshint())
        .pipe(jshint.reporter('default'));
});
gulp.task('default', ['bundle', 'lint']);
