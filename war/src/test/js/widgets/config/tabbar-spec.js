var jsTest = require("jenkins-js-test");

describe("tabbar-spec tests", function () {

    it("- test section count", function (done) {
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

    it("- test section activation", function (done) {
        jsTest.onPage(function() {
            var configTabBar = jsTest.requireSrcModule('widgets/config/tabbar');
            var firstTableMetadata = configTabBar.addTabsOnFirst();
            
            // The first section ("General") should be active by default
            expect(firstTableMetadata.activeSection().id).toBe('config_general');
            expect(firstTableMetadata.activeSectionCount()).toBe(1);
            
            // Mimic the user clicking on one of the tabs. Should make that section active,
            // with all of the rows in that section having an "active" class. 
            firstTableMetadata.activateSection('config__workflow');
            expect(firstTableMetadata.activeSectionCount()).toBe(1);
            var activeSection = firstTableMetadata.activeSection();
            expect(activeSection.id).toBe('config__workflow');
            expect(activeSection.activeRowCount()).toBe(3);
            expect(firstTableMetadata.topRows.filter('.active').size()).toBe(3); // should be the same as activeSection.activeRowCount()            
            
            done();
        }, 'widgets/config/workflow-config.html');
    });
});

// TODO: lots more tests !!!