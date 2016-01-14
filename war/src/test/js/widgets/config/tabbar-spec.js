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
            expect($('.tabBar .tab').size()).toBe(8);

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

            firstTableMetadata.onShowSection(function() {
                expect(this.id).toBe('config__workflow');

                expect(firstTableMetadata.activeSectionCount()).toBe(1);
                var activeSection = firstTableMetadata.activeSection();
                expect(activeSection.id).toBe('config__workflow');
                expect(activeSection.activeRowCount()).toBe(3);
                expect(firstTableMetadata.topRows.filter('.active').size()).toBe(3); // should be the same as activeSection.activeRowCount()

                done();
            });

            // Mimic the user clicking on one of the tabs. Should make that section active,
            // with all of the rows in that section having an "active" class.
            firstTableMetadata.activateSection('config__workflow');
            // above 'firstTableMetadata.onShowSection' handler should get called now

        }, 'widgets/config/workflow-config.html');
    });

    it("- test row-set activation", function (done) {
        jsTest.onPage(function() {
            var configTabBar = jsTest.requireSrcModule('widgets/config/tabbar');
            var firstTableMetadata = configTabBar.addTabsOnFirst();

            var generalSection = firstTableMetadata.activeSection();
            expect(generalSection.id).toBe('config_general');
            expect(generalSection.rowSets.length).toBe(2);
            expect(generalSection.getRowSetLabels().toString()).toBe('Discard Old Builds,This build is parameterized');
            expect(generalSection.rowSets[0].rows.length).toBe(4);
            expect(generalSection.rowSets[1].rows.length).toBe(4);

            done();
        }, 'widgets/config/workflow-config.html');
    });
});

// TODO: lots more tests !!!