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

exports.markConfigTable = function(configTable) {
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
    var configTableMetadata = [];
    var curSection = {
        title: 'General'
    };

    exports.filterRows(topRows);

    configTableMetadata.push(curSection);
    curSection.id = exports.toId(curSection.title);
    configTableMetadata.configTable = configTable;
    configTableMetadata.topRows = topRows;

    topRows.each(function () {
        var tr = $(this);
        if (tr.hasClass('section-header-row')) {
            // a new section
            var title = tr.attr('title');
            curSection = {
                title: title,
                id: exports.toId(title)
            };
            configTableMetadata.push(curSection);
        }

        tr.addClass(curSection.id);
    });

    var buttonsRow = $('#bottom-sticker', configTable).closest('tr');
    buttonsRow.removeClass(curSection.id);
    buttonsRow.addClass(exports.toId('buttons'));
    
    return configTableMetadata;
};

exports.markConfigTables = function() {
    var $ = jQD.getJQuery();
    var configTablesMetadata = [];
    var configTables = exports.findConfigTables();

    // Mark the config tables.
    configTables.addClass('config-table');

    // For each table (is there ever more than one?)
    configTables.each(function() {
        configTablesMetadata.push(exports.markConfigTable($(this)));
    });

    return configTablesMetadata;
};

exports.filterRows = function(topRows, selector) {
    topRows.hide();
    topRows.filter(selector).show();

    // and always show the buttons
    topRows.filter('.config_buttons').show();
};

exports.toId = function(string) {
    return 'config_' + string.replace(/[\W_]+/g, '_').toLowerCase();
};