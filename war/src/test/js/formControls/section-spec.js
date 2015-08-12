var jsTest = require("jenkins-js-test");
var jquery = require('jquery-detached-2.1.4');

describe("form section behavior tests", function () {

    it("- test for sections", function (done) {
   
        jsTest.onPage(function() {
          
            var section = jsTest.requireSrcModule('formControls/section');
            var $ = jquery.getJQuery();
            section.init();
            
            var $sections = $('[data-tagName="section"]');
                
            //get each section and click its header....                
            $sections.each(function(i,elem){
              var $section = $(elem);
              var $header = $section.children('.panel-section-header');
              var $body = $section.children('.panel-collapse');
              
              
              $header.click();
              expect($section.hasClass('not-shown')).toBe(true);
              

            });
            
            expect($sections.length > 0 ).toBe(true);
            
            done();

        }, 'formControls/configForm.html');
    });
}); 

