/*
 * Internal support module for config tables.
 */

var jQD = require('jquery-detached');

exports.findConfigTables = function() {
    var $ = jQD.getJQuery();
    // The config tables are the immediate child <table> elements of <form> elements
    // with a name of "config"?
    return $('form[name="config"] > table');
};

exports.decorateConfigTable = function(configTable) {
    var $ = jQD.getJQuery();
    var sectionHeaders = $('.section-header', configTable);

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

    // Go through the top level <tr> elements (immediately inside the <tbody>)
    // and group the related <tr>s based on the "section-header-row", using a "normalized"
    // version of the section title as the section id.
    var tbody = $('> tbody', configTable);
    var topRows = $('> tr', tbody);
    var configTableMetadata = new ConfigTableMetaData(configTable, topRows);
    var curSection = new ConfigSection(configTableMetadata, 'General');

    configTableMetadata.sections.push(curSection);
    curSection.id = exports.toId(curSection.title);

    topRows.each(function () {
        var tr = $(this);
        if (tr.hasClass('section-header-row')) {
            // a new section
            var title = tr.attr('title');
            curSection = new ConfigSection(configTableMetadata, title);
            configTableMetadata.sections.push(curSection);
        }

        curSection.rows.push(tr);
        tr.addClass(curSection.id);
    });

    var buttonsRow = $('#bottom-sticker', configTable).closest('tr');
    buttonsRow.removeClass(curSection.id);
    buttonsRow.addClass(exports.toId('buttons'));
    
    return configTableMetadata;
};

exports.toId = function(string) {
    string = string.trim();
    return 'config_' + string.replace(/[\W_]+/g, '_').toLowerCase();
};

/*
 * =======================================================================================
 * Configuration table section.
 * =======================================================================================
 */
function ConfigSection(parentCMD, title) {
    this.parentCMD = parentCMD;
    this.title = title;
    this.id = exports.toId(title);
    this.rows = [];
    this.rowSets = undefined;
}

/*
 * Set the element (jquery) that activates the section (on click).
 */
ConfigSection.prototype.setActivator = function(activator) {
    this.activator = activator;
    
    var section = this;
    section.activator.click(function() {
        section.parentCMD.showSection(section);
    });
};

ConfigSection.prototype.activate = function() {
    if (this.activator) {
        this.activator.click();
    } else {
        console.warn('No activator attached to config section object.');
    }
};

ConfigSection.prototype.activeRowCount = function() {
    var activeRowCount = 0;
    for (var i = 0; i < this.rows.length; i++) {
        if (this.rows[i].hasClass('active')) {
            activeRowCount++;
        }
    }
    return activeRowCount;
};

ConfigSection.prototype.updateRowSetVisibility = function() {
    if (this.rowSets === undefined) {
        // Lazily gather rowset information.
        this.gatherRowSets();
    }
    for (var i = 0; i < this.rowSets.length; i++) {
        var rowSet = this.rowSets[i];
        if (rowSet.toggleWidget !== undefined) {
            var isChecked = rowSet.toggleWidget.is(':checked');
            for (var ii = 0; ii < rowSet.rows.length; ii++) {
                if (isChecked) {
                    rowSet.rows[ii].show();
                } else {
                    rowSet.rows[ii].hide();
                }
            }
        }
    }
};

ConfigSection.prototype.gatherRowSets = function() {
    this.rowSets = [];
    
    // Only tracking row-sets that are bounded by 'row-set-start' and 'row-set-end' (for now).
    // Also, only capturing the rows after the 'block-control' input (checkbox, radio etc)
    // and before the 'row-set-end'.
    // TODO: Find out how these actually work. It seems like they can be nested into a hierarchy :(
    // Also seems like you can have these "optional-block" thingies which are not wrapped
    // in 'row-set-start' etc. Grrrrrr :( 
    
    var curRowSet = undefined; // jshint ignore:line
    for (var i = 0; i < this.rows.length; i++) {
        var row = this.rows[i];
        
        if (row.hasClass('row-set-start')) {
            curRowSet = new ConfigRowSet(row);
            curRowSet.findToggleWidget(row);
        } else if (curRowSet !== undefined) {
            if (row.hasClass('row-set-end')) {
                curRowSet.endRow = row;
                // Only capture the row-set if we find a 'row-set-end'.
                // Yeah, this does not handle hierarchical stuff (see above TO-DO).
                this.rowSets.push(curRowSet); 
                curRowSet = undefined;
            } else if (curRowSet.toggleWidget === undefined) {
                curRowSet.findToggleWidget(row);
            } else {
                // we have the toggleWidget, which means that this row is
                // one of the rows after that row and is one of the rows that's
                // subject to being made visible/hidden when the input is
                // checked or unchecked.
                curRowSet.rows.push(row);
            }
        }
    }
};

ConfigSection.prototype.getRowSetLabels = function() {
    var labels = [];
    for (var i = 0; i < this.rowSets.length; i++) {
        var rowSet = this.rowSets[i];
        if (rowSet.label) {
            labels.push(rowSet.label);
        }
    }
    return labels;
};

/*
 * =======================================================================================
 * Configuration table section.
 * =======================================================================================
 */
function ConfigRowSet(startRow) {
    this.startRow = startRow;
    this.rows = [];
    this.endRow = undefined;
    this.toggleWidget = undefined;
    this.label = undefined;
}

/*
 * Find the row-set toggle widget i.e. the input element that indicates that
 * the row-set rows should be made visible or not.
 */
ConfigRowSet.prototype.findToggleWidget = function(row) {
    var $ = jQD.getJQuery();
    var input = $(':input.block-control', row);
    if (input.size() === 1) {
        this.toggleWidget = input;
        this.label = input.parent().find('label').text();
        input.addClass('disable-behavior');
    }
};

/*
 * =======================================================================================
 * ConfigTable MetaData class.
 * =======================================================================================
 */
function ConfigTableMetaData(configTable, topRows) {
    this.configTable = configTable;
    this.topRows = topRows;
    this.sections = [];
    this.showListeners = [];
}

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

    if (section) {
        var $ = jQD.getJQuery();

        // Deactive currently active section ...
        $('.config-section-activator.active', this.activatorContainer).removeClass('active');
        this.topRows.filter('.active').removeClass('active');
        this.topRows.hide();

        // Active the specified section
        section.activator.addClass('active');
        this.topRows.filter('.' + section.id).addClass('active').show();

        // Hide the section header row. No need for it now because the
        // tab text acts as the section label.
        $('.section-header-row').hide();

        // and always show the buttons
        this.topRows.filter('.config_buttons').show();
        
        // Update the row-set visibility
        section.updateRowSetVisibility();

        fireListeners(this.showListeners, section);
    }
};

ConfigTableMetaData.prototype.onShowSection = function(listener) {
    this.showListeners.push(listener);
};

function fireListeners(listeners, contextObject) {
    for (var i = 0; i < listeners.length; i++) {
        fireListener(listeners[0], contextObject);
    }    
    function fireListener(listener, contextObject) {
        listener.call(contextObject);
    }
}