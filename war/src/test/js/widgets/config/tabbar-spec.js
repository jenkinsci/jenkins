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

    it("- test finder - via handler triggering", function (done) {
        jsTest.onPage(function() {
            var configTabBarWidget = jsTest.requireSrcModule('widgets/config/tabbar');
            var configTabBar = configTabBarWidget.addTabsOnFirst();
            var jQD = require('jquery-detached');

            var $ = jQD.getJQuery();

            var tabBar = $('.tabBar');

            // All tabs should be visible...
            expect($('.tab', tabBar).size()).toBe(4);
            expect($('.tab.hidden', tabBar).size()).toBe(0);

            var finder = configTabBar.findInput;
            expect(finder.size()).toBe(1);

            // Find sections that have the text "trigger" in them...
            keydowns('trigger', finder);

            // Need to wait for the change to happen ... there's a 300ms delay.
            // We could just call configTabBar.showSections(), but ...
            setTimeout(function() {
                expect($('.tab.hidden', tabBar).size()).toBe(3);
                expect(textCleanup($('.tab.hidden', tabBar).text())).toBe('General|#Advanced Project Options|#Build');

                var activeSection = configTabBar.activeSection();
                expect(textCleanup(activeSection.title)).toBe('#Build Triggers');

                expect($('.highlight-split .highlight').text()).toBe('Trigger');

                done();
            }, 600);
        }, 'widgets/config/freestyle-config.html');
    });

    it("- test finder - via showSections()", function (done) {
        jsTest.onPage(function() {
            var configTabBarWidget = jsTest.requireSrcModule('widgets/config/tabbar');
            var configTabBar = configTabBarWidget.addTabsOnFirst();
            var jQD = require('jquery-detached');

            var $ = jQD.getJQuery();

            var tabBar = $('.tabBar');

            configTabBar.showSections('quiet period');
            expect($('.tab.hidden', tabBar).size()).toBe(3);
            expect(textCleanup($('.tab.hidden', tabBar).text())).toBe('General|#Build Triggers|#Build');

            var activeSection = configTabBar.activeSection();
            expect(textCleanup(activeSection.title)).toBe('#Advanced Project Options');

            done();
        }, 'widgets/config/freestyle-config.html');
    });

    it("- test finder - via showSections() - in inner row-group", function (done) {
        jsTest.onPage(function() {
            var configTabBarWidget = jsTest.requireSrcModule('widgets/config/tabbar');
            var configTabBar = configTabBarWidget.addTabsOnFirst();
            var jQD = require('jquery-detached');

            var $ = jQD.getJQuery();

            var tabBar = $('.tabBar');

            configTabBar.showSections('Strategy');
            expect($('.tab.hidden', tabBar).size()).toBe(3);
            expect(textCleanup($('.tab.hidden', tabBar).text())).toBe('#Advanced Project Options|#Build Triggers|#Build');

            var activeSection = configTabBar.activeSection();
            expect(textCleanup(activeSection.title)).toBe('General');

            done();
        }, 'widgets/config/freestyle-config.html');
    });

    function keydowns(text, onInput) {
        var jQD = require('jquery-detached');
        var $ = jQD.getJQuery();

        // hmmm, for some reason, the key events do not result in the text being
        // set in the input, so setting it manually.
        onInput.val(text);

        // Now fire a keydown event to trigger the handler
        var e = $.Event("keydown");
        e.which = 116;
        onInput.trigger(e);
    }

    function textCleanup(text) {
        return text.trim().replace(/(\r\n|\n|\r)/gm, "").replace(/  +/g, "|");
    }
});

// TODO: lots more tests !!!