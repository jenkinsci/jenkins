/*
 * Internal support module for config tables.
 */

var jQD = require('../../../util/jquery-ext.js');
var ConfigSection = require('./ConfigSection.js');
var util = require('./util.js');

exports.markConfigTableParentForm = function(configTable) {
    var form = configTable.closest('form');
    form.addClass('jenkins-config');
    return form;
};

exports.findConfigTables = function() {
    var $ = jQD.getJQuery();
    // The config tables are the immediate child <table> elements of <form> elements
    // with a name of "config"?
    return $('form[name="config"] > table');
};

exports.fromConfigTable = function(configTable) {
    var $ = jQD.getJQuery();
    var sectionHeaders = $('.section-header', configTable);
    var configForm = exports.markConfigTableParentForm(configTable);

    // Mark the ancestor <tr>s of the section headers and add a title
    sectionHeaders.each(function () {
        var sectionHeader = $(this);
        var sectionRow = sectionHeader.closest('tr');
        var sectionTitle = sectionRow.text();

        // Remove leading hash from accumulated text in title (from <a> element).
        if (sectionTitle.indexOf('#') === 0) {
            sectionTitle = sectionTitle.substring(1);
        }

        sectionRow.addClass('section-header-row');
        sectionRow.attr('title', sectionTitle);
    });

    var configTableMetadata = new ConfigTableMetaData(configForm, configTable);
    var topRows = configTableMetadata.getTopRows();
    var firstRow = configTableMetadata.getFirstRow();
    var curSection;

    // The first set of rows don't have a 'section-header-row', so we manufacture one,
    // calling it a "General" section. We do this by marking the first row in the table.
    // See the next block of code.
    
    if(!firstRow.hasClass('section-header-row')){
      var generalRow = $('<tr class="section-header-row insert first" title="General"><td colspan="4"><div class="section-header"><a class="section-anchor">#</a>General</div></td></tr>');
      firstRow.before(generalRow);
      firstRow = configTableMetadata.getFirstRow();
      var newArray = $.makeArray(topRows);
      newArray.unshift(generalRow[0]);
      topRows = $(newArray);
    }

    firstRow.addClass('section-header-row');
    firstRow.attr('title', "General");

    // Go through the top level <tr> elements (immediately inside the <tbody>)
    // and group the related <tr>s based on the "section-header-row", using a "normalized"
    // version of the section title as the section id.
    topRows.each(function () {
        var tr = $(this);
        if (tr.hasClass('section-header-row')) {
            // a new section
            curSection = new ConfigSection(tr, configTableMetadata);
            configTableMetadata.sections.push(curSection);
        }
    });

    var buttonsRow = $('#bottom-sticker', configTable).closest('tr');
    buttonsRow.removeClass(curSection.id);
    buttonsRow.addClass(util.toId('buttons'));

    return configTableMetadata;
};

/*
 * =======================================================================================
 * ConfigTable MetaData class.
 * =======================================================================================
 */
function ConfigTableMetaData(configForm, configTable) {
    this.$ = jQD.getJQuery();
    this.configForm = configForm;
    this.configTable = configTable;
    this.configTableBody = this.$('> tbody', configTable);
    this.activatorContainer = undefined;
    this.sections = [];
    this.findInput = undefined;
    this.showListeners = [];
    this.configWidgets = undefined;
    this.addWidgetsContainer();
    this.addFindWidget();
}

ConfigTableMetaData.prototype.getTopRows = function() {
    return this.configTableBody.children('tr');
};

ConfigTableMetaData.prototype.getFirstRow = function() {
    return this.getTopRows().first();
};

ConfigTableMetaData.prototype.addWidgetsContainer = function() {
    var $ = jQD.getJQuery();
    this.configWidgets = $('<div class="jenkins-config-widgets"></div>');
    this.configWidgets.insertBefore(this.configForm);
};

ConfigTableMetaData.prototype.addFindWidget = function() {
    var $ = jQD.getJQuery();
    var thisTMD = this;
    var findWidget = $('<div class="find-container"><div class="find"><span title="Clear" class="clear">x</span><input placeholder="find"/></div></div>');

    thisTMD.findInput = $('input', findWidget);

    // Add the find text clearer
    $('.clear', findWidget).click(function() {
        thisTMD.findInput.val('');
        thisTMD.showSections('');
        thisTMD.findInput.focus();
    });

    var findTimeout;
    thisTMD.findInput.keydown(function() {
      
      //keydown needs to wait a pulse for the value to be applied to the input.
      setTimeout(function(){
        var val = thisTMD.findInput.val();
        
        //if the keydown does not set a value, this filtering should exit.
        if(val === ''){
          return false;
        }
      
        if (findTimeout) {
            clearTimeout(findTimeout);
            findTimeout = undefined;
        }
        findTimeout = setTimeout(function() {
            findTimeout = undefined;
            thisTMD.showSections(thisTMD.findInput.val());
        }, 300);
      },1);
    });

    this.configWidgets.append(findWidget);
};

ConfigTableMetaData.prototype.sectionCount = function() {
    return this.sections.length;
};

ConfigTableMetaData.prototype.hasSections = function() {
    var hasSections = (this.sectionCount() > 0);
    if (!hasSections) {
        console.warn('Jenkins configuration without sections?');
    }
    return  hasSections;
};

ConfigTableMetaData.prototype.sectionIds = function() {
    var sectionIds = [];
    for (var i = 0; i < this.sections.length; i++) {
        sectionIds.push(this.sections[i].id);
    }
    return sectionIds;
};

ConfigTableMetaData.prototype.activateSection = function(sectionId) {
    if (!sectionId) {
        throw 'Invalid section id "' + sectionId + '"';
    }

    var section = this.getSection(sectionId);
    if (section) {
        section.activate();
    }
};

ConfigTableMetaData.prototype.activeSection = function() {
    if (this.hasSections()) {
        for (var i = 0; i < this.sections.length; i++) {
            var section = this.sections[i];
            if (section.activator.hasClass('active')) {
                return section;
            }
        }
    }
};

ConfigTableMetaData.prototype.getSection = function(sectionId) {
    if (this.hasSections()) {
        for (var i = 0; i < this.sections.length; i++) {
            var section = this.sections[i];
            if (section.id === sectionId) {
                return section;
            }
        }
    }
    return undefined;
};

ConfigTableMetaData.prototype.removeSection = function(sectionId) {
    if (this.hasSections()) {
        for (var i = 0; i < this.sections.length; i++) {
            var section = this.sections[i];
            if (section.id === sectionId) {
                this.sections.splice(i, 1);
                if (section.activator) {
                    section.activator.remove();
                }
                return true;
            }
        }
    }
    return false;
};

ConfigTableMetaData.prototype.activateFirstSection = function() {
    if (this.hasSections()) {
        this.activateSection(this.sections[0].id);
    }
};

ConfigTableMetaData.prototype.activeSectionCount = function() {
    var activeSectionCount = 0;
    if (this.hasSections()) {
        for (var i = 0; i < this.sections.length; i++) {
            var section = this.sections[i];
            if (section.activator.hasClass('active')) {
                activeSectionCount++;
            }
        }
    }
    return activeSectionCount;
};

ConfigTableMetaData.prototype.showSection = function(section) {
    if (typeof section === 'string') {
        section = this.getSection(section);
    }
    if(!section) {return;}

    var $ = this.$;
    var $header = $(section.headerRow).show();
    var scrollTop = $header.offset().top - ($('#main-panel .jenkins-config-widgets').outerHeight() + 15);

    $('html,body').animate({
      scrollTop: scrollTop
    }, 500);
    setTimeout(function(){
      section.activator.closest('.tabBar').find('.active').removeClass('active');
      section.activator.addClass('active');
    },510);
    if (section) {
        var topRows = this.getTopRows();

        // Deactivate currently active section ...
        this.deactivateActiveSection();

        // Active the specified section
        section.activator.addClass('active');
        section.markRowsAsActive();

        // and always show the buttons
        topRows.filter('.config_buttons').show();

        // Update text highlighting
        section.highlightText(this.findInput.val());

        fireListeners(this.showListeners, section);
    }
};

ConfigTableMetaData.prototype.deactivateActiveSection = function(hideRows) {
    var topRows = this.getTopRows();
    var $ = jQD.getJQuery();

    $('.config-section-activator.active', this.activatorContainer).removeClass('active');
    topRows.filter('.active').removeClass('active');
    if(hideRows) 
    	{topRows.hide();}
};

ConfigTableMetaData.prototype.onShowSection = function(listener) {
    this.showListeners.push(listener);
};

ConfigTableMetaData.prototype.showSections = function(withText) {
    if (withText === '') {
        if (this.hasSections()) {
            for (var i1 = 0; i1 < this.sections.length; i1++) {
                this.sections[i1].activator.removeClass('hidden');
            }
            var activeSection = this.activeSection();
            if (!activeSection) {
                this.showSection(this.sections[0]);
            } else {
        	this.deactivateActiveSection(true);
                activeSection.highlightText(this.findInput.val());
            }
        }
    } else {
        if (this.hasSections()) {
            var sectionsWithText = [];

            for (var i2 = 0; i2 < this.sections.length; i2++) {
                var section = this.sections[i2];

                if (section.hasText(withText)) {
                    section.activator.removeClass('hidden');
                    sectionsWithText.push(section);
                } else {
                    section.activator.addClass('hidden');
                }
            }

            // Select the first section to contain the text.
            if (sectionsWithText.length > 0) {
                this.showSection(sectionsWithText[0]);
            } else {
                this.deactivateActiveSection();
            }
        }
    }
};

function fireListeners(listeners, contextObject) {
    for (var i = 0; i < listeners.length; i++) {
        fireListener(listeners[i], contextObject);
    }
    function fireListener(listener, contextObject) {
        setTimeout(function() {
            listener.call(contextObject);
        }, 1);
    }
}