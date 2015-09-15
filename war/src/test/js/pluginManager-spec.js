var jsTest = require("jenkins-js-test");

describe("pluginManager.js", function () {

    it("- simple show and go test", function (done) {
        jsTest.onPage(function() {
            var pm = jsTest.requireSrcModule('pluginManager.js');
            
            // Catch the ajax call and test it
            require("./util").mockAjax(function(callOpts) {
                expect(callOpts.url).toBe('/jenkins/pluginManager/install');
                expect(callOpts.data.dynamicLoad).toBe(true);
                expect(callOpts.data['plugin.github']).toBe(true);
                expect(callOpts.data['plugin.workflow-aggregator']).toBe(true);
                done();
            });
            
            pm.installPlugins(['github', 'workflow-aggregator']);
        });
    });
});