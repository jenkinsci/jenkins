var jsTest = require("jenkins-js-test");

describe("form submission doSubit tests", function () {

    it("- test xyz", function (done) {
        jsTest.onPage(function(window) {
            var formsub = jsTest.requireSrcModule('formsub');

            expect(formsub).toBeDefined();

            done();

        }, '<div id="myform">The form is great!!</div>');
    });
});
