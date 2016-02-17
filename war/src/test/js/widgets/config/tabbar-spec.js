var jsTest = require("jenkins-js-test");

describe("tabbar-spec tests", function () {

    it("- test section count", function (done) {
        jsTest.onPage(function() {
            var configTabBar = jsTest.requireSrcModule('widgets/config/tabbar');
            var firstTableMetadata = configTabBar.addTabsOnFirst();

            var jQD = require('jquery-detached');
            var $ = jQD.getJQuery();

            expect($('.section-header-row', firstTableMetadata.configTable).size()).toBe(4);
            expect(firstTableMetadata.sectionCount()).toBe(4);
            expect($('.tabBar .tab').size()).toBe(4);

            expect(firstTableMetadata.sectionIds().toString())
                .toBe('config_general,config__advanced_project_options,config__build_triggers,config__build');

            done();
        }, 'widgets/config/freestyle-config.html');
    });

    it("- test section activation", function (done) {
        jsTest.onPage(function() {
            var configTabBar = jsTest.requireSrcModule('widgets/config/tabbar');
            var firstTableMetadata = configTabBar.addTabsOnFirst();

            // The first section ("General") should be active by default
            expect(firstTableMetadata.activeSection().id).toBe('config_general');
            expect(firstTableMetadata.activeSectionCount()).toBe(1);

            firstTableMetadata.onShowSection(function() {
                expect(this.id).toBe('config__build');

                expect(firstTableMetadata.activeSectionCount()).toBe(1);
                var activeSection = firstTableMetadata.activeSection();
                expect(activeSection.id).toBe('config__build');
                expect(activeSection.activeRowCount()).toBe(2);
                expect(firstTableMetadata.getTopRows().filter('.active').size()).toBe(1); // should be activeSection.activeRowCount() - 1

                done();
            });

            // Mimic the user clicking on one of the tabs. Should make that section active,
            // with all of the rows in that section having an "active" class.
            firstTableMetadata.activateSection('config__build');
            // above 'firstTableMetadata.onShowSection' handler should get called now

        }, 'widgets/config/freestyle-config.html');
    });

    it("- test row-group modeling", function (done) {
        jsTest.onPage(function() {
            var configTabBar = jsTest.requireSrcModule('widgets/config/tabbar');
            var firstTableMetadata = configTabBar.addTabsOnFirst();

            var generalSection = firstTableMetadata.activeSection();
            expect(generalSection.id).toBe('config_general');

            var sectionRowGroups = generalSection.rowGroups;

            expect(sectionRowGroups.length).toBe(1);
            expect(sectionRowGroups[0].getRowCount(false)).toBe(0); // zero because it does not have any non row-group rows nested immediately inside i.e. does not have any "normal" rows
            expect(sectionRowGroups[0].getRowCount(true)).toBe(4); // there are some nested down in the children. see below
            expect(sectionRowGroups[0].rowGroups.length).toBe(1);
            expect(sectionRowGroups[0].rowGroups[0].getRowCount(false)).toBe(4); // The inner grouping has rows
            expect(sectionRowGroups[0].rowGroups[0].getRowCount()).toBe(4); // Same as above ... just making sure they're direct child rows and not nested below
            expect(generalSection.getRowGroupLabels().toString()).toBe('Discard Old Builds');

            done();
        }, 'widgets/config/freestyle-config.html');
    });
});

// TODO: lots more tests !!!