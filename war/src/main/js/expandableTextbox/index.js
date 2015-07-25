console.log('expandableTextbox/index.js');
var jquery = require('jquery-detached-2.1.4');
var booty = require('bootstrap-detached-3.3');


function removeItem(e){
  e.preventDefault();
  var $teaxtarea = $('textarea');
  var attrs = $input[0].attributes;
  debugger; 
}

exports.init = function() {

  var $ = jquery.getJQuery(); 
  $(this.findElem());
  return this;

};

exports.findElem = function() {
  var $ = jquery.getJQuery();
  var thisObj = this;
  var $textbox= $('[data-tagName="expandableTextbox"]')
    .each(function(i,elem){
      var $elem = $(elem);
      var $input = $elem.find('.textbox-area > .setting-input');
      var $btn = $elem.find('.expand-btn > a.btn')
        .click(removeItem);
    });      
  
  
  
  return $textbox;
};

