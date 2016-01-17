var jsTest = require("jenkins-js-test");

describe("Config tabbar tests", function () {

    it("- test", function (done) {
        jsTest.onPage(function() {
            var configTabBar = jsTest.requireSrcModule('widgets/config/tabbar');
            var firstTableMetadata = configTabBar.addTabsOnFirst();
            
            var jQD = require('jquery-detached');
            var $ = jQD.getJQuery();
            
            expect($('.section-header-row', firstTableMetadata.configTable).size()).toBe(5);
            expect(firstTableMetadata.sectionCount()).toBe(4);
            expect($('.tabBar .tab').size()).toBe(4);
            
            expect(firstTableMetadata.sectionIds().toString())
                .toBe('config_general,config__build_triggers,config__advanced_project_options,config__workflow');
            
            done();
        }, 'widgets/config/workflow-config.html');
    });
});

// TODO: lots more tests !!!