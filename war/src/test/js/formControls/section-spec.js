var jsTest = require("jenkins-js-test");
var jquery = require('jquery-detached-2.1.4');

describe("form section behavior tests", function () {

    it("- test for sections", function (done) {
   
        jsTest.onPage(function() {
            
            var section = jsTest.requireSrcModule('formControls/section');
            var $ = jquery.getJQuery();
            section.init();
            
            var $sections = $('[data-tagName="section"]');
            
            function clickSections(i,elem){
              var $section = $(this);
              var $header = $section.children('.panel-section-header');
              var $body = $section.children('.panel-collapse');
              var wasOpen = !$section.hasClass('not-shown');
              var opening = $section.hasClass('opening');
              
              $header.click();
              if(opening){
                //if opening, expect nothing to change after click and state to be going to shown...
                expect($section.hasClass('not-shown')).toBe(!wasOpen);
                expect($section.hasClass('opening')).toBe(opening);
                expect($section.hasClass('shown')).toBe(true);
              }              
              else{
                if(wasOpen){
                  // if it started open, now make sure it starts closing...
                  expect($section.hasClass('not-shown')).toBe(true);
                  expect($body.attr('style')).toEqual('height: 0px;'); 
                }
                else{
                  expect($section.hasClass('shown')).toBe(true);
                  expect($section.hasClass('opening')).toBe(true);
                }
              }
            }
            
            //click section header to close...                
            $sections.each(clickSections);
            //click section header to open...  
            $sections.each(clickSections);
            
            //wait and see if each section finishes opening...
            setTimeout(function(){

              $sections.each(clickSections);
              setTimeout(function(){
                $sections.each(clickSections);
                done();
                
              },500);
            },100);
            
            expect($sections.length > 0 ).toBe(true);


            

        }, 'formControls/configForm.html');
    },100000);
}); 

