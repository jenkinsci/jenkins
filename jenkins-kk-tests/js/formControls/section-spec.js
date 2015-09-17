var jsTest = require("jenkins-js-test");

describe("form section behavior tests", function () {

    it("- test init", function (done) {
      /*
        jsTest.onPage(function() {
          
            var section = jsTest.requireSrcModule('formControls/section');
            var $ = jquery.getJQuery();
            var init = section.init();
            
            expect(section).toBeDefined();
            expect(init.resize).toBeDefined();

            describe("...methods and behaviors",function(){
              it("- test openCloseEventHandler", function(done){
                //get each section and click its header....
                
                $.each('[data-tagName="section"]',function(i,elem){
                  var $section = $(elem);
                  var $header = $section.children('.panel-section-header');
                  var $body = $section.children('.panel-collapse');
                  $header.trigger('click');
                  
                  expect($section.hasClass('not-shown')).toBe(true);
                  expect($body.height() === 0).toBe(true);
                  
                  $header.trigger('click');
                  expect($body.height() === $body.children().outerHeight()).toBe(true);
                  
                  
                  
                });
                
   
                
                done();
              });
            })
            
            done();

        }, 'formControls/configForm.html');*/
    });
}); 