var jsTest = require("jenkins-js-test");

describe("form section behavior tests", function () {

    it("- test init", function (done) {
        jsTest.onPage(function(window) {
            var section = jsTest.requireSrcModule('formControls/section');
            var $ = jquery.getJQuery();
            var init = section.init();
            
            expect(section).toBeDefined();
            expect(init.resize).toBeDefined();

            describe("...methods and behaviors",function(){
              it("- test openCloseEventHandler", function(done){
                
                done();
              });
            })
            
            done();

        }, 'formControls/configForm.html');
    });
});