// Initialize all modules by requiring them. Also makes sure they get bundled (see gulpfile.js).
var $ = require('jquery-detached').getJQuery();
require('remodal-detached').getRemodal();

var getItems = function() {
  var d = $.Deferred();
  $.get('itemCategories?depth=3').done(
    function(data){
      d.resolve(data);
    }
  );
  return d.promise();
}; 

var jRoot = $('head').attr('data-rooturl');

$.when(getItems()).done(function(data) {
  $(function() {

    //////////////////////////
    // helper functions...

    function parseResponseFromCheckJobName(data) {
      var html = $.parseHTML(data);
      var element = html[0];
      if (element !== undefined) {
        return $(element).text();
      }
      return undefined;
    }

    function cleanClassName(className){
      return className.replace(/\./g,'_');
    }

    function checkForLink(desc) {
      if (desc.indexOf('&lt;a href="') === -1) {
        return desc;
      }
      var newDesc = desc.replace(/\&lt;/g,'<').replace(/\&gt;/g,'>');
      return newDesc;
    }

    function getCopyFromValue() {
      return $('input[type="text"][name="from"]', '#createItem').val();
    }

    function isItemNameEmpty() {
      var itemName = $('input[name="name"]', '#createItem').val();
      return (itemName === '') ? true : false;
    }

    function getFieldValidationStatus(fieldId) {
      return $('#' + fieldId).data('valid');
    }

    function setFieldValidationStatus(fieldId, status) {
      $('#' + fieldId).data('valid', status);
    }

    function activateValidationMessage(messageId, context, message) {
      if (message !== undefined && message !== '') {
        $(messageId, context).html('&#187; ' + message);
      }
      cleanValidationMessages(context);
      hideInputHelp(context);
      $(messageId).removeClass('input-message-disabled');
    }

    function cleanValidationMessages(context) {
      $(context).find('.input-validation-message').addClass('input-message-disabled');
    }

    function hideInputHelp(context) {
      $('.input-help', context).addClass('input-message-disabled');
    }

    function showInputHelp(context) {
      $('.input-help', context).removeClass('input-message-disabled');
    }

    function enableSubmit(status) {
      var btn = $('form .footer .btn-decorator button[type=submit]');
      if (status === true) {
        if (btn.hasClass('disabled')) {
          btn.removeClass('disabled');
          btn.prop('disabled', false);
        }
        btn.focus();
      } else {
        if (!btn.hasClass('disabled')) {
          btn.addClass('disabled');
          btn.prop('disabled', true);
        }
      }
    }

    function getFormValidationStatus() {
      if (getFieldValidationStatus('name') && (getFieldValidationStatus('items') || getFieldValidationStatus('from'))) {
          return true;
      }
      return false;
    }

    function cleanItemSelection() {
      $('.categories').find('li[role="radio"]').attr("aria-checked", "false");
      $('#createItem').find('input[type="radio"][name="mode"]').removeAttr('checked');
      $('.categories').find('.active').removeClass('active');
      setFieldValidationStatus('items', false);
    }

    function cleanCopyFromOption() {
      $('#createItem').find('input[type="radio"][value="copy"]').removeAttr('checked');
      $('input[type="text"][name="from"]', '#createItem').val('');
      setFieldValidationStatus('from', false);
    }


    //////////////////////////////////
    // Draw functions

    function drawCategory(category) {
      var $category = $('<div/>').addClass('category row').attr('id', 'j-add-item-type-' + cleanClassName(category.id));
      var $items = $('<ul/>').addClass('j-item-options');
      var $catHeader = $('<div class="header" />');
      var title = '<h2>' + category.name + '</h2>';
      var description = '<p>' + category.description + '</p>';

      // Add items
      $.each(category.items, function(i, elem) {
        $items.append(drawItem(elem));
      });

      $catHeader.append(title);
      $catHeader.append(description);
      $category.append($catHeader);
      $category.append($items);

      return $category;
    }

    function drawItem(elem) {
      var desc = checkForLink(elem.description);
      var $item = $(['<li tabindex="0" role="radio" aria-checked="false" class="', cleanClassName(elem.class), ' col-sm-12"><label><input type="radio" name="mode" value="',
      elem.class ,'"/> <span class="label">', elem.displayName, '</span></label></li>'].join('')).append(['<div class="desc">', desc, '</div>'].join('')).append(drawIcon(elem));

      function select(e) {
        e.preventDefault();
        cleanCopyFromOption();
        cleanItemSelection();

        $(this).attr("aria-checked", "true");
        $(this).find('input[type="radio"][name="mode"]').prop('checked', true);
        $(this).addClass('active');

        setFieldValidationStatus('items', true);
        if (!getFieldValidationStatus('name')) {
          $('input[name="name"][type="text"]', '#createItem').focus();
        } else {
          if (getFormValidationStatus()) {
            enableSubmit(true);
          }
        }
      }

      $item.click(select);

      $item.keypress(function(e) {
        switch (e.which) {
          case 13:
          case 32:
            $(this).trigger('click');
            e.stopPropagation();
            break;
        }
      });

      return $item;
    }

    function drawIcon(elem) {
      var $icn;
      if (elem.iconFilePathPattern) {
        $icn = $('<div class="icon">');
        var iconFilePath = jRoot + '/' + elem.iconFilePathPattern.replace(":size", "48x48");
        $(['<img src="', iconFilePath, '">'].join('')).appendTo($icn);
      } else {
        $icn = $('<div class="default-icon">');
        var colors = ['c-49728B','c-335061','c-D33833','c-6D6B6D', 'c-6699CC'];
        var desc = elem.description || '';
        var name = elem.displayName;
        var colorClass= colors[(desc.length) % 4];
        var aName = name.split(' ');
        var a = name.substring(0,1);
        var b = ((aName.length === 1) ? name.substring(1,2) : aName[1].substring(0,1));
        $(['<span class="a">',a,'</span><span class="b">',b,'</span>'].join('')).appendTo($icn);
        $icn.addClass(colorClass);
      }
      return $icn;
    }

    // The main panel content is hidden by default via an inline style. We're ready to remove that now.
    $('#add-item-panel').removeAttr('style');

    // Render all categories
    var $categories = $('div.categories');
    $.each(data.categories, function(i, elem) {
      drawCategory(elem).appendTo($categories);
    });

    // Focus
    $("#add-item-panel").find("#name").focus();

    // Init NameField
    $('input[name="name"]', '#createItem').blur(function() {
      if (!isItemNameEmpty()) {
        var itemName = $('input[name="name"]', '#createItem').val();
        $.get("checkJobName", { value: itemName }).done(function(data) {
          var message = parseResponseFromCheckJobName(data);
          if (message !== '') {
            activateValidationMessage('#itemname-invalid', '.add-item-name', message);
          } else {
            cleanValidationMessages('.add-item-name');
            showInputHelp('.add-item-name');
            setFieldValidationStatus('name', true);
            if (getFormValidationStatus()) {
              enableSubmit(true);
            }
          }
        });
      } else {
        enableSubmit(false);
        setFieldValidationStatus('name', false);
        cleanValidationMessages('.add-item-name');
        activateValidationMessage('#itemname-required', '.add-item-name');
      }
    });

    // Init CopyFromField
    $('input[name="from"]', '#createItem').blur(function() {
      if (getCopyFromValue() === '') {
        $('#createItem').find('input[type="radio"][value="copy"]').removeAttr('checked');
      } else {
        cleanItemSelection();
        $('#createItem').find('input[type="radio"][value="copy"]').prop('checked', true);
        setFieldValidationStatus('from', true);
        if (!getFieldValidationStatus('name')) {
          activateValidationMessage('#itemname-required', '.add-item-name');
          setTimeout(function() {
            $('input[name="name"][type="text"]', '#createItem').focus();
          }, 400);
        } else {
          if (getFormValidationStatus()) {
            enableSubmit(true);
          }
        }
      }
    });

    // Client-side validation
    $("#createItem").submit(function(event) {
      if (!getFormValidationStatus()) {
        event.preventDefault();
        if (!getFieldValidationStatus('name')) {
          activateValidationMessage('#itemname-required', '.add-item-name');
          $('input[name="name"][type="text"]', '#createItem').focus();
        } else {
          if (!getFieldValidationStatus('items') && !getFieldValidationStatus('from'))  {
            activateValidationMessage('#itemtype-required', '.add-item-name');
            $('input[name="name"][type="text"]', '#createItem').focus();
          }
        }
      }
    });

    // Disable the submit button
    enableSubmit(false);

  });
});
