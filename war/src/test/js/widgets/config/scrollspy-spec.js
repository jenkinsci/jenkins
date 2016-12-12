var jsTest = require("jenkins-js-test");

require('./mocks');

describe("scrollspy-spec tests", function () {

    it("- test scrolling", function (done) {
        jsTest.onPage(function () {
            var manualScroller = newManualScroller();
            var tabbars = jsTest.requireSrcModule('config-scrollspy.js');
            tabbars.scrollspeed = 1; // speed up the scroll speed for testing

            var tabbar = tabbars.tabbars[0];

            // We need to trick it into thinking that the sections have
            // some height. We need height if we want to scroll.
            doSectionFunctionMocking(tabbar);

            // console.log('**** ' + tabbar.sectionIds());
            // **** config_general,config__advanced_project_options,config__build_triggers,config__build

            var scrollToLog = [];
            var click_scrollto_done = false;
            var manual_scrollto_done = true;
            tabbars.on(function (event) {
                if (event.type === 'manual_scrollto') {
                    manual_scrollto_done = true;
                }
                if (event.type === 'click_scrollto') {
                    expect(event.section.id).toBe('config__build');
                    click_scrollto_done = true;
                }
                scrollToLog.push(event.section.id);

                if (click_scrollto_done && manual_scrollto_done) {
                    var scrollEvents = JSON.stringify(scrollToLog);
                    // see the calls to manualScroller.scrollTo (below)
                    if (scrollEvents === '["config_general","config__advanced_project_options","config__build_triggers","config_general","config__build"]') {
                        done();
                    }
                }
            });

            // Lets mimic scrolling. This should trigger the
            // scrollspy into activating different sections
            // as the user scrolls down the page.
            // See the test console output (gulp test) for a printout
            // of the positions/offsets of each section.
            // i.e. ...
            //	    config_general: 100
            //	    config__advanced_project_options: 140
            //	    config__build_triggers: 180
            //	    config__build: 220

            manualScroller.scrollTo(100);
            manualScroller.scrollTo(140);
            manualScroller.scrollTo(180);

            // Scrolling to the last section offset will not trigger its
            // tab into being activated. Search for "### < 75% ADVANCED"
            // in config-scrollspy.js
            // So, "config__build" should not be added to 'scrollToLog' (see above).
            // But, a manual click on the tab (vs a scroll) should result in it
            // being added later.
            manualScroller.scrollTo(220); // This will not trigger a

            // Scroll back to the General section ... that should work and log
            // to 'scrollToLog' (see above).
            manualScroller.scrollTo(100);

            // Now, manually activate the the "General" section i.e. "activate" it.
            // This should result in 'config__build' being added to 'scrollToLog',
            // which is not what happens if you try scrolling to this last section
            // (see above).
            tabbar.getSection('config__build').activate();
        }, 'widgets/config/freestyle-config-scrollspy.html');
    });
});

function doSectionFunctionMocking(tabbar) {
    function doMocks(section, viewportEntryOffset) {
        section.getViewportEntryOffset = function() {
            return viewportEntryOffset;
        };
    }

    var mainOffset = 100;
    var height = 40;
    console.log('*** Mocking the position/offset of the form sections:');
    for (var i = 0; i < tabbar.sections.length; i++) {
        var section = tabbar.sections[i];
        var offset = (mainOffset + (height * i));
        console.log('\t' + section.id + ': ' + offset);
        doMocks(section, offset);
    }
}

function newManualScroller() {
    var page = jsTest.requireSrcModule('util/page.js');
    var scrollListeners = [];
    var curScrollToTop = 0;

    page.winScrollTop = function() {
        return curScrollToTop;
    };
    page.onWinScroll = function(listener) {
        scrollListeners.push(listener);
    };
    return {
        scrollTo: function(position) {
            curScrollToTop = position;
            for (var i = 0; i < scrollListeners.length; i++) {
                scrollListeners[i]();
            }
        }
    };
}