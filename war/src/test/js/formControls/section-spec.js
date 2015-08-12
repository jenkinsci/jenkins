var jsTest = require("jenkins-js-test");
var jquery = require('jquery-detached-2.1.4');

describe("form section behavior tests", function () {

    it("- test for sections", function (done) {
   
        jsTest.onPage(function() {
          
            var section = jsTest.requireSrcModule('formControls/section');
            var $ = jquery.getJQuery();
            var $sections = $('[data-tagName="section"]');
                
            //get each section and click its header....                
            $sections.each(function(i,elem){
              var $section = $(elem);
              var $header = $section.children('.panel-section-header');
              var $body = $section.children('.panel-collapse');
              
              
              $header.trigger('click');
              //TODO: need to add event spy from Jasmine Jquery
            });
            
            expect($sections.length > 0 ).toBe(true);
            
            done();

        }, 'formControls/configForm.html');
    });
}); 

