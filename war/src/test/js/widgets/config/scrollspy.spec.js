import fs from 'fs';
import path from 'path';
import jsTest from '@jenkins-cd/js-test';
import { mockBehaviorShim } from './mocks';

const debug = false;

const htmlContent = fs.readFileSync(
    path.resolve(__dirname, './freestyle-config-scrollspy.html'),
    'utf8'
);

describe("scrollspy-spec tests", function () {
    // Need to mock the utils/page module because we will hijack the scroll events
    const mockPageUtils = jest.requireActual('../../../../main/js/util/page');
    const mockWinScrollTop = jest.fn();
    const mockOnWinScroll = jest.fn();

    // Need to mock the .isVisible() function of the ConfigSection model because
    // it needs to return true for these tests and its current implementation
    // would return false
    const mockConfigSection = jest.requireActual(
        '../../../../main/js/widgets/config/model/ConfigSection'
    );

    beforeEach(() => {
        mockBehaviorShim();

        jest.mock('../../../../main/js/util/page', () => ({
            __esModule: true,
            ...mockPageUtils,
            default: {
                ...mockPageUtils.default,
                fireBottomStickerAdjustEvent: jest.fn(),
                winScrollTop: mockWinScrollTop,
                onWinScroll: mockOnWinScroll,
            }
        }));

        mockConfigSection.default.prototype.isVisible = jest.fn();
        jest.mock(
            '../../../../main/js/widgets/config/model/ConfigSection',
            () => mockConfigSection
        );
    });

    afterEach(() => {
        // Mock cleanup so that it will not affect other tests
        jest.resetAllMocks();
        jest.resetModules();
    });

    // Declared here to take advantage of the mockPageUtils in the closure scope
    function newManualScroller() {
        var scrollListeners = [];
        var curScrollToTop = 0;

        mockWinScrollTop.mockImplementation(() => curScrollToTop);
        mockOnWinScroll.mockImplementation((listener) => {
            scrollListeners.push(listener);
        })

        return {
            scrollTo: function(position) {
                curScrollToTop = position;
                for (var i = 0; i < scrollListeners.length; i++) {
                    scrollListeners[i]();
                }
            }
        };
    }

    it("- test scrolling", function (done) {
        // Needs to return true for the tests
        mockConfigSection.default.prototype.isVisible.mockReturnValue(true);

        jsTest.onPage(function () {
            document.documentElement.innerHTML = htmlContent;

            var manualScroller = newManualScroller();
            // eslint-disable-next-line no-undef
            var tabbars = require('../../../../main/js/config-scrollspy');
            tabbars.setScrollspeed(1); // speed up the scroll speed for testing

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
            // See the test console output (yarn test) for a printout
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

            // Now, manually activate the "General" section i.e. "activate" it.
            // This should result in 'config__build' being added to 'scrollToLog',
            // which is not what happens if you try scrolling to this last section
            // (see above).
            tabbar.getSection('config__build').activate();

        // Need to pass the HTML string because jsTest will not load the content
        // if it only receives a filename
        }, htmlContent);
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
    if (debug) {
        console.log('*** Mocking the position/offset of the form sections:');
    }
    for (var i = 0; i < tabbar.sections.length; i++) {
        var section = tabbar.sections[i];
        var offset = (mainOffset + (height * i));
        if (debug) {
            console.log('\t' + section.id + ': ' + offset);
        }
        doMocks(section, offset);
    }
}
