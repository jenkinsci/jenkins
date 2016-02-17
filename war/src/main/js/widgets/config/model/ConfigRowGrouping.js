var jQD = require('../../../util/jquery-ext.js');

module.exports = ConfigRowGrouping;

/*
 * =======================================================================================
 * Configuration table row grouping i.e. row-set-*, optional-block-*, radio-block-* etc
 * 
 * A ConfigSection maintains a list of ConfigRowGrouping and then ConfigRowGrouping
 * itself maintains a list i.e. it's hierarchical. See ConfigSection.gatherRowGroups().
 * =======================================================================================
 */
function ConfigRowGrouping(startRow, parentRowGroupContainer) {
    this.startRow = startRow;
    this.parentRowGroupContainer = parentRowGroupContainer;
    this.endRow = undefined;
    this.rows = [];
    this.rowGroups = []; // Support groupings nested inside groupings
    this.toggleWidget = undefined;
    this.label = undefined;
}

ConfigRowGrouping.prototype.getRowCount = function(includeChildren) {
    var count = this.rows.length;
    if (includeChildren === undefined || includeChildren === true) {
        for (var i = 0; i < this.rowGroups.length; i++) {
            count += this.rowGroups[i].getRowCount();
        }
    }
    return count;
};

ConfigRowGrouping.prototype.getLabels = function() {
    var labels = [];
    
    if (this.label) {
        labels.push(this.label);
    }
    for (var i = 0; i < this.rowGroups.length; i++) {
        var rowSet = this.rowGroups[i];
        labels.push(rowSet.getLabels());
    }
    return labels;
};

ConfigRowGrouping.prototype.updateVisibility = function() {
    if (this.toggleWidget !== undefined) {
        var isChecked = this.toggleWidget.is(':checked');
        for (var i = 0; i < this.rows.length; i++) {
            if (isChecked) {
                this.rows[i].show();
            } else {
                this.rows[i].hide();
            }
        }
    }
    for (var ii = 0; ii < this.rowGroups.length; ii++) {
        var rowSet = this.rowGroups[ii];
        rowSet.updateVisibility();        
    }
};

/*
 * Find the row-set toggle widget i.e. the input element that indicates that
 * the row-set rows should be made visible or not.
 */
ConfigRowGrouping.prototype.findToggleWidget = function(row) {
    var $ = jQD.getJQuery();
    var input = $(':input.block-control', row);
    if (input.size() === 1) {
        this.toggleWidget = input;
        this.label = input.parent().find('label').text();
        input.addClass('disable-behavior');
    }
};
