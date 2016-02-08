var jQD = require('../../../util/jquery-ext.js');
var util = require('./util.js');
var ConfigRowSet = require('./ConfigRowSet.js');

module.exports = ConfigSection;

/*
 * =======================================================================================
 * Configuration table section.
 * =======================================================================================
 */
function ConfigSection(parentCMD, headerRow) {
    this.parentCMD = parentCMD;
    this.headerRow = headerRow;
    this.title = headerRow.attr('title');
    this.id = util.toId(this.title);
    this.rowSets = undefined;
    this.activator = undefined;
}

/*
 * Get the section rows.
 */
ConfigSection.prototype.getRows = function() {
    var curTr = this.headerRow.next();
    var rows = [];
    var numNewRows = 0;

    rows.push(curTr);
    while(curTr.size() === 1 && !curTr.hasClass('section-header-row')) {
        rows.push(curTr);
        if (!curTr.hasClass(this.id)) {
            numNewRows++;
            curTr.addClass(this.id);
        }
        curTr = curTr.next();
    }
    
    if (numNewRows > 0) {
        // We have new rows in the section ... reset cached info.
        this.rowSets = undefined;
    }
    
    return rows;
};

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

ConfigSection.prototype.markRowsAsActive = function() {
    var rows = this.getRows();
    for (var i = 0; i < rows.length; i++) {
        rows[i].addClass('active').show();
    }
};

ConfigSection.prototype.activeRowCount = function() {
    var activeRowCount = 0;
    var rows = this.getRows();
    for (var i = 0; i < rows.length; i++) {
        if (rows[i].hasClass('active')) {
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
        rowSet.updateVisibility();
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

    var rows = this.getRows();
    if (rows.length > 0) {
        // Create a top level "fake" ConfigRowSet just to capture
        // the top level groupings. We copy the rowSets info out
        // of this and use it in the top "this" ConfigSection instance. 
        var rowSetContainer = new ConfigRowSet(rows[0], undefined);

        this.rowSets = rowSetContainer.rowSets;

        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];

            if (row.hasClass('row-group-start')) {
                var newRowSet = new ConfigRowSet(row, rowSetContainer);
                rowSetContainer.rowSets.push(newRowSet);
                rowSetContainer = newRowSet;
                newRowSet.findToggleWidget(row);
            } else {
                if (row.hasClass('row-group-end')) {
                    rowSetContainer.endRow = row;
                    rowSetContainer = rowSetContainer.parentRowSetContainer; // pop back off the "stack"
                } else if (rowSetContainer.toggleWidget === undefined) {
                    rowSetContainer.findToggleWidget(row);
                } else {
                    // we have the toggleWidget, which means that this row is
                    // one of the rows after that row and is one of the rows that's
                    // subject to being made visible/hidden when the input is
                    // checked or unchecked.
                    rowSetContainer.rows.push(row);
                }
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

ConfigSection.prototype.highlightText = function(text) {
    var $ = jQD.getJQuery();
    var selector = ":containsci('" + text + "')";
    var rows = this.getRows();
    
    for (var i = 0; i < rows.length; i++) {
        var row = rows[i];

        /*jshint loopfunc: true */
        $('span.highlight-split', row).each(function() { // jshint ignore:line
            var highlightSplit = $(this);
            highlightSplit.before(highlightSplit.text());
            highlightSplit.remove();
        });

        if (text !== '') {
            var regex = new RegExp('(' + text + ')',"gi");

            /*jshint loopfunc: true */
            $(selector, row).find(':not(:input)').each(function() {
                var $this = $(this);
                $this.contents().each(function () {
                    // We specifically only mess with text nodes
                    if (this.nodeType === 3) {
                        var highlightedMarkup = this.wholeText.replace(regex, '<span class="highlight">$1</span>');
                        $(this).replaceWith('<span class="highlight-split">' + highlightedMarkup + '</span>');
                    }
                });
            });
        }
    }
};
