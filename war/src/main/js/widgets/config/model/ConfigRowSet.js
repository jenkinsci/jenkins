var jQD = require('../../../util/jquery-ext.js');

module.exports = ConfigRowSet;

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
