var jsTest = require("jenkins-js-test");
var jquery = require('jquery-detached-2.1.4');

describe("findAncestor", function () {

    it("- find TR", function (done) {
        jsTest.onPage(function(window) {
//            var findMod = jsTest.requireSrcModule('find');
//            var $ = jquery.getJQuery();
//            var startEl = $('.start-el');
//
//            // endEl should be marked with a class of 'end-el' see test2.html
//            var endEl = findMod.findAncestor(startEl.get(), 'tr');
//
//            console.log(endEl);
//
//            expect($(endEl).hasClass('end-el')).toBe(true);

            done();
        }, 'find/findAncestor/test1.html');
    });
});
