var jsTest = require("jenkins-js-test");

describe("form submission doSubit tests", function () {

    it("- test xyz", function (done) {
        jsTest.onPage(function(window) {
            var formsub = require('../../../main/js/formsub'); // TODO: clean this up - add a srcRequire function to 'jenkins-js-test' and eliminate all the relative path shenanigans

            var doSubmit = formsub.doSubmit('#myform');

            console.log('*** The form element contains: "' + doSubmit + '"');

            done();

        }, '<div id="myform">The form is great!!</div>');
    });
});
