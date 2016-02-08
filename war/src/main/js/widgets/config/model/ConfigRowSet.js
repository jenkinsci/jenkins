var jQD = require('../../../util/jquery-ext.js');

module.exports = ConfigRowSet;

/*
 * =======================================================================================
 * Configuration table row grouping i.e. row-set-*, optional-block-*,
 * =======================================================================================
 */
function ConfigRowSet(startRow, parentRowSetContainer) {
    this.startRow = startRow;
    this.parentRowSetContainer = parentRowSetContainer;
    this.endRow = undefined;
    this.rows = [];
    this.rowSets = [];
    this.toggleWidget = undefined;
    this.label = undefined;
}

ConfigRowSet.prototype.updateVisibility = function() {
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
    for (var ii = 0; ii < this.rowSets.length; ii++) {
        var rowSet = this.rowSets[ii];
        rowSet.updateVisibility();        
    }
};

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
