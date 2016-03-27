// Initialize all modules by requiring them. Also makes sure they get bundled (see gulpfile.js).
var $ = require('jquery-detached').getJQuery();

var getItems = function(){
  var d = $.Deferred();
  $.get('categories?depth=3').done(
      function(data){
        d.resolve(data);
      }
  );
  return d.promise();
}; 

var jRoot = $('head').attr('data-rooturl');

$.when(getItems()).done(function(data){
  $(function() {

    // The main panel content is hidden by default via an inline style. We're ready to remove that now.
    $('#create-item-panel').removeAttr('style');

    //////////////////////////////
    // helpful reference DOM

    //var defaultMinToShow = 2;
    //var defaultLooseItems = 'jenkins.category.uncategorized';
    var $form = $('form[name="createItem"]');
    var $navBox = $('<nav class="navbar navbar-default navbar-static form-config tabBarFrame"/>');
    var $widgetBox = $('<div class="jenkins-config-widgets" />');
    var $categories = $('div.categories');
    var $subBtn = $('#bottom-sticker .yui-submit-button');
    var $nameInput;
    //var sectionsToShow = [];

    $widgetBox.prepend($navBox);
    
    ////////////////////////////////
    // submit button click
    
    /*function makeButtonWrapper(){
      var $p = window.$; // jshint ignore:line
      var btn = $p('ok'); 
      var okButton = window.makeButton(btn, null); // jshint ignore:line 

      $subBtn = $('#bottom-sticker .yui-submit-button');
      
      checkFormReady();
      
      $subBtn.on('click',function() {
        $(this).addClass('yui-button-disabled yui-submit-button-disabled')
          .find('button')
            .attr('disabled','disabled')
            .text('. . .');
      });
    }*/

    ////////////////////////////////
    // scroll action......

    //var isManualScrolling = false;
    //var ignoreNextScrollEvent = false;
    //var $window = $(window);
    //var $breadcrumbBar = $('#breadcrumbBar');
    //var $createItemPanel = $('#create-item-panel');
    //var createPanelOffset = $createItemPanel.offset().top;

    /*function autoActivateTabs(){
      if (isManualScrolling === true) {
        // We ignore scroll events when a manual scroll is in
        // operation e.g. when the user clicks on a category tab.
        return;
      }      
      if (ignoreNextScrollEvent === true) {
        // Things like repositioning of the tabbar can trigger scroll
        // events that we want to ignore.
        ignoreNextScrollEvent = false;
        return;
      }

      var winScoll = $window.scrollTop();

      $widgetBox.find('.active').removeClass('active');
      $.each(data.categories,function(i,cat){
        var domId = '#j-add-item-type-'+cat.id;
        var $cat = $(domId);
        var catHeight = ($cat.length > 0)?
        $cat.offset().top + $cat.outerHeight() - createPanelOffset: 0;

        if(winScoll < catHeight){
          var $thisTab = $widgetBox.find(['[href="',cleanHref(domId),'"]'].join(''));
          resetActiveTab($thisTab);
          return false;
        }
      });
    }*/

    /*function stickTabbar() {
      var winScoll = $window.scrollTop();
      var setWidth = function() {
          $widgetBox.width($form.outerWidth() - 2);
      };

      if(winScoll > createPanelOffset - $breadcrumbBar.height()){
        setWidth();
        $widgetBox.css({
          'position':'fixed',
          'top':($breadcrumbBar.height())+'px'});
        $categories.css({'margin-top':$widgetBox.outerHeight()+'px'});
        $window.resize(setWidth);
        return true;
      } else{
        $widgetBox.add($categories).removeAttr('style');
        $window.unbind('resize', setWidth);
        return false;
      }
    }*/

    //////////////////////////
    // helper functions...

    function checkFormReady(){
      //make sure everyone has changed and gotten attached...
      setTimeout(function(){
        var $name = $form.removeClass('no-select').find('input[name="name"]').removeClass('no-val');
        
        function checkItems(){
          var selected = $form.find('input[type="radio"]:checked').length > 0;
          var named = $.trim($name.val()).length > 0;
          return {selected:selected,named:named};
        }
        if(checkItems().selected && checkItems().named){
          $subBtn.removeClass('yui-button-disabled').find('button').removeAttr('disabled');
        }
        else{
          $subBtn.addClass('yui-button-disabled').find('button').attr('disabled','disabled');
        }
      },10);
    }
    
    /*function addCopyOption(data){
      var $copy = $('#copy').closest('tr');
      if($copy.length === 0) {return data;} // exit if copy should not be added to page. Jelly page holds that logic.
      var copyTitle = $copy.find('label').text();
      var copyDom = $copy.next().find('.setting-main').html();
      var copy = {
          name:'Copy',
          id:'copy',
          minToShow:0,
          items:[
            {
              class:"copy",
              description:copyDom,
              displayName:copyTitle,
              iconFilePathPattern:'images/48x48/copy.png'
            }
          ]
      };
      var newData = [];

      $.each(data,function(i,elem){
        if(elem.id !== "category-id-copy")
          { newData.push(elem); }
      });
      
      newData.push(copy);

      return newData;
    }*/

    function checkForLink(desc){
      if(desc.indexOf('&lt;a href="') === -1) {
        return false;
      }
      var newDesc = desc.replace(/\&lt;/g,'<').replace(/\&gt;/g,'>');
      return newDesc;
    }

    /*function checkCatCount(elem){
      var minToShow = (typeof elem.minToShow === 'number')? elem.minToShow : defaultMinToShow;
      var showIt = ($.isArray(elem.items) && elem.items.length >= minToShow);
      return showIt;
    }*/

    function cleanClassName(className){
      return className.replace(/\./g,'_');
    }

    /*function cleanHref(id,reverse){
      if(reverse){
        var gotHash = (id.indexOf('#') === 0)? 
           '#j-add-item-type-'+ id.substring(1).replace(/\./g,'_'):
             'j-add-item-type-'+ id.replace(/\./g,'_');
        return gotHash;
      }
      else{
        return id.replace('j-add-item-type-','');
      }
    }*/

    /*function cleanLayout(){
      // Do a little shimmy-hack to force legacy code to resize correctly and set tab state.
      $('html,body').animate({scrollTop: 1}, 1);
      $('html,body').animate({scrollTop: 0}, 10);

      setTimeout(fireBottomStickerAdjustEvent,410);
    }*/

    /*function resetActiveTab($this){
      var $nav = $this.closest('.nav');
      $nav.find('.active').removeClass('active');
      $this.addClass('active');
    }*/

    //////////////////////////////////
    // Draw functions

    /*function drawName() {
      var $name = $('<div class="j-add-item-name" />');

      $nameInput = $('<input type="text" name="name" class="name" id="name" placeholder="New item name..." />')
        .keyup(function(){
          $form.find('input[name="name"]').val($(this).val());
          checkFormReady();
        })
        .appendTo($name);

      $widgetBox.prepend($name);
      setTimeout(function(){
        $nameInput.focus();
      },100);
    }*/

    /*function drawTabs(data){
      //$('#main-panel').addClass('container');
      var $nav = $('<ul class="nav navbar-nav tabBar config-section-activators" />');
      
      $.each(data,function(i,elem) {

        // little bit hacky here... need to keep track if I have tabs to show, so if there is just 1, I can hide it later....
        if (elem.minToShow !== 0 && checkCatCount(elem)) {
          sectionsToShow.push(elem.id);
        }
        
        var $tab = drawTab(i,elem);
        var $items = drawCategory(elem);
        var $cat = $items.parent();
        
        $.each(elem.items, function(i, elem) {
          var $item = drawItem(elem);
          $items.append($item);
        });
        
        if (checkCatCount(elem)) {
          $nav.append($tab);
        }
        $categories.append($cat);

      });
      //$(window).on('scroll', autoActivateTabs);
      //$(window).on('scroll', stickTabbar);
   
      if (sectionsToShow.length > 0){
        $navBox.append($nav);
      } else {
        $categories.find('.header').hide();
        $categories.addClass('flat');
      }
      drawName();
      cleanLayout();
    }*/

    /*function drawTab(i, elem){
      if (!elem) {
        elem = i;
      }
      var $tab = $(['<li><a class="tab ' , ((i === 0) ? 'active' : ''), '" href="#', cleanHref(elem.id), '">', elem.name,'</a></li>'].join('')).click(function() {
          var $this = $(this).children('a');
          //var tab = $this.attr('href');
          //var scrollTop = $(cleanHref(tab, true)).offset().top - ($newView.children('.jenkins-config-widgets').height() + 15);

          setTimeout(function() {
            resetActiveTab($this);
          }, 510);

          /*isManualScrolling = true;
          $('html,body').animate({
            scrollTop: scrollTop
          }, 500, function() {
            isManualScrolling = false;
            ignoreNextScrollEvent = stickTabbar();
          });
        });
      return $tab;
    }*/

    function drawCategory(category) {
      var $category = $('<div/>').addClass('category').attr('id', 'j-add-item-type-'+cleanClassName(category.id));
      var $items = $('<ul/>').addClass('j-item-options');
      var $catHeader = $('<div class="header" />');
      var $title = ['<h2>', category.name, '</h2>'];
      var $description = ['<p>', category.description, '</p>'];

      // if there are enough items for a category, attach the category and its header...
      /*if (checkCatCount(category)) {
        var $catHeader = $('<div class="category-header" />').prependTo($category);
        var catheader = ['<h2>', category.name, '</h2>'].join('');
        var catDesc = ['<p>', category.description, '</p>'].join('');

        if ((category.minToShow > 0)) {
          $(catheader).appendTo($catHeader);
          $(catDesc).appendTo($catHeader);
        }
      } else {
        var targ = category.remainders || defaultLooseItems;
        $items = $('#'+cleanHref(targ,true)).find('.j-item-options');
      }*/

      // Add items
      $.each(category.items, function(i, elem) {
        $items.append(drawItem(elem));
      });

      $catHeader.append($title);
      $catHeader.append($description);
      $category.append($catHeader);
      $category.append($items);

      return $category;
    }

    function drawItem(elem) {
      var desc = (checkForLink(elem.description)) ? checkForLink(elem.description) : elem.description;
      var $item = $([
          '<li class="',cleanClassName(elem.class),'"><label><input name="mode" value="',elem.class,'" type="radio" /> <span class="label">', elem.displayName, '</span></label></li>'
      ].join('')).append([
          '<div class="desc">', desc, '</div>'
      ].join('')).append(drawIcon(elem));

      function setSelectState(e) {
        e.preventDefault();
        var href = $(e.target).attr('href');
        if (href) {
          window.open(href);
        }
        
        var $this = $(this).closest('li');
        $this.closest('.categories').find('input[type="radio"][name="mode"]').removeAttr('checked');

        //if this is a hyperlink, don't move the selection.
        if ($this.find('a:focus').length === 1) {
          return false;
        }

        $this.closest('.categories').find('.active').removeClass('active');
        $this.addClass('active');
        $this.find('input[type="radio"]').prop('checked', true);
        checkFormReady();

        if ($nameInput.val() === '') {
          $nameInput.focus();
        }
      }

      $item.click(setSelectState);

      return $item;
    }

    function drawIcon(elem){
      var $icn = $('<div class="icn">');
      if (!elem.iconFilePathPattern) {
        var colors = ['c-49728B','c-335061','c-D33833','c-6D6B6D','c-DCD9D8','other'];
        var desc = elem.description || '';
        var name = elem.displayName;
        var colorClass= colors[(desc.length) % 6];
        var aName = name.split(' ');
        var a = name.substring(0,1);
        var b = ((aName.length === 1) ? name.substring(1,2) : aName[1].substring(0,1));
        $([
          '<span class="dfIcn"><span class="a">',a,'</span><span class="b">',b,'</span></span>'
         ].join(''))
          .appendTo($icn);
        return $icn.addClass('df').addClass(colorClass);
      }

      var iconFilePath = jRoot + '/' + elem.iconFilePathPattern.replace(":size", "48x48");
      $(['<span class="img" style="background:url(', iconFilePath, ')"></span>'].join('')).appendTo($icn);

      return $icn;
    }

    //var categoriesWithCopy = addCopyOption(data.categories);

    // makeButtonWrapper();
    // drawTabs(data.categories);

    // Render all categories
    $.each(data.categories, function(i, elem) {
      drawCategory(elem).appendTo($categories);
    });
  });
});

/*function fireBottomStickerAdjustEvent() {
  Event.fire(window, 'jenkins:bottom-sticker-adjust'); // jshint ignore:line
}*/