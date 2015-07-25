console.log('optionBlock/index.js');
var jquery = require('jquery-detached-2.1.4');

var tagName = '[data-tagName="optionalBlock"]'; //what DOM element am I attached to...
var scope = 'form'; //how much of the DOM do I need to watch for changes...

exports.init = function() {
  var $ = jquery.getJQuery();
  var thisObj = this;
  $(function(){
    $(scope).on('DOMNodeInserted',thisObj.findElems);
  });
  return this;

};

exports.findElems = function() {
  var $ = jquery.getJQuery();
  var $elems = $(tagName)
    .each(function(i,elem){
      var $elem = $(elem);
      var $groupBox = $elem;
      var $group = $groupBox.children('.option-group').first();
      var $labelBox = $elem.children('.option-group-label');
      var $label = $labelBox.find('label');
      var $chk = $labelBox.find('.chk-name > input');
      var checked = $chk.is(':checked');

      if($group.children().length === 0)
        $groupBox.addClass('none');
      else
        $groupBox.removeClass('none');
      
      if(checked){ 
        $group.show();
        $groupBox.addClass('shown');
      }
      else{
        $group.hide();
        $groupBox.removeClass('shown');
      }
      
    });        
  
  return $elems;
};