/*
The MIT Licence, for code from kryogenix.org

Code downloaded from the Browser Experiments section
of kryogenix.org is licenced under the so-called MIT
licence. The licence is below.

Copyright (c) 1997-date Stuart Langridge

Permission is hereby granted, free of charge, to any
person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the
Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the
Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF
ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
OR OTHER DEALINGS IN THE SOFTWARE.
*/
/*
Usage
=====

Add the "sortable" CSS class to a table to make it sortable.
The first column must be always table header, and the rest must be table data.
(the script seems to support rows to be fixed at the bottom, but haven't figured out how to use it.)

If the table data is sorted to begin with, you can add 'initialSortDir="up|down"' to the
corresponding column in the header row to display the direction icon from the beginning.
This is recommended to provide a visual cue that the table can be sorted.

The script guesses the table data, and try to use the right sorting algorithm.
But you can override this behavior by having 'data="..."' attribute on each row,
in which case the sort will be done on that field.
*/
function ts_makeSortable(table) {
    var firstRow;
    if (table.rows && table.rows.length > 0) {
        firstRow = table.rows[0];
    }
    if (!firstRow) return;

    // We have a first row: assume it's the header, and make its contents clickable links
    for (var i=0;i<firstRow.cells.length;i++) {
        var cell = firstRow.cells[i];
        var txt = ts_getInnerText(cell);

        var initialSortDir = arrowTable[cell.getAttribute("initialSortDir")];
        if(initialSortDir==null)    initialSortDir=arrowTable.none;
        cell.innerHTML = '<a href="#" class="sortheader" onclick="ts_resortTable(this);return false;">'+txt+
                         '<span class="sortarrow">'+initialSortDir.text+'</span></a>';

        if(initialSortDir!=arrowTable.none)
            cell.firstChild.lastChild.sortdir = initialSortDir;
    }
    ts_loadDirection(table);
    ts_refresh(table);
}

function ts_getInnerText(el) {
	if (typeof el == "string") return el;
	if (typeof el == "undefined") { return el };
	if (el.innerText) return el.innerText;	//Not needed but it is faster
	var str = "";

	var cs = el.childNodes;
	var l = cs.length;
	for (var i = 0; i < l; i++) {
		switch (cs[i].nodeType) {
			case 1: //ELEMENT_NODE
				str += ts_getInnerText(cs[i]);
				break;
			case 3:	//TEXT_NODE
				str += cs[i].nodeValue;
				break;
		}
	}
	return str;
}

// extract data for sorting from a cell
function extractData(x) {
  if(x==null) return '';
  var data = x.getAttribute("data");
  if(data!=null)
    return data;
  return ts_getInnerText(x);
}

var arrowTable = {
    up: {
        id: "up",
        text: "&nbsp;&nbsp;&uarr;"
    },
    down: {
        id: "down",
        text: "&nbsp;&nbsp;&darr;"
    },
    none: {
        id: "none",
        text: "&nbsp;&nbsp;&nbsp;"
    },
    lnkRef: null
}

arrowTable.up.next = arrowTable.down;
arrowTable.down.next = arrowTable.up;

function ts_resortTable(lnk) {
    // get the span
    var span = lnk.lastChild;
    var spantext = ts_getInnerText(span);
    var th = lnk.parentNode;
    var column = th.cellIndex;
    var table = getParent(th,'TABLE');

    // Work out a type for the column
    if (table.rows.length <= 1) return;
    var itm = extractData(table.rows[1].cells[column]).trim();
    var sortfn = ts_sort_caseinsensitive;
    if (itm.match(/^\d\d[\/-]\d\d[\/-]\d\d\d\d$/)) sortfn = ts_sort_date;
    if (itm.match(/^\d\d[\/-]\d\d[\/-]\d\d$/)) sortfn = ts_sort_date;
    if (itm.match(/^[�$]/)) sortfn = ts_sort_currency;
    if (itm.match(/^-?[\d\.]+$/)) sortfn = ts_sort_numeric;
    var firstRow = new Array();
    var newRows = new Array();
    for (i=0;i<table.rows[0].length;i++) { firstRow[i] = table.rows[0][i]; }
    for (j=1;j<table.rows.length;j++) { newRows[j-1] = table.rows[j]; }

    var dir = span.sortdir;
    if (arrowTable.lnkRef != lnk) {
        if (dir == null) dir = arrowTable.up;
    } else {
        dir = dir.next; // new sort direction
    }
    var sortfn2 = sortfn;
    if(dir === arrowTable.up) {
        // ascending
        sortfn2 = function(a,b){return -sortfn(a,b)};
    }
    newRows.sort(function(a,b) {
        return sortfn2(
            extractData(a.cells[column]),
            extractData(b.cells[column]));
    });

    arrowTable.lnkRef = lnk; // make column sort down only if column selected is same as last
    span.sortdir = dir;

    // We appendChild rows that already exist to the tbody, so it moves them rather than creating new ones
    // don't do sortbottom rows
    for (var i=0;i<newRows.length;i++) {
        if (! Element.hasClassName(newRows[i], 'sortbottom'))
            table.tBodies[0].appendChild(newRows[i]);
    }
    // do sortbottom rows only
    for (var i=0;i<newRows.length;i++) {
        if (Element.hasClassName(newRows[i], 'sortbottom'))
            table.tBodies[0].appendChild(newRows[i]);
    }

    // Delete any other arrows there may be showing
    var allspans = table.rows[0].getElementsByTagName("span");
    for (var ci=0;ci<allspans.length;ci++) {
        if (allspans[ci].className == 'sortarrow') {
            allspans[ci].innerHTML = arrowTable.none.text;
        }
    }

    span.innerHTML = dir.text;
    ts_saveDirection(table, column, dir);
}

/**
 * Call when data has changed.
 * @since 1.484
 */
function ts_refresh(table){
    var column;
    var dir;
    var key = ts_getStorageKey(table);
    var val = ts_Storage.hasKey(key) ? ts_Storage.getItem(key) : null;
    if (val != null) {
        var vals = val.split(":");
        if(vals.length != 2) {
            return;
        }
        column = parseInt(vals[0]);
        dir = vals[1];
    } else {
        var firstRow;
        if (table.rows && table.rows.length > 0) {
            firstRow = table.rows[0];
        }
        if (!firstRow) {
            return;
        }
        for (var i=0;i<firstRow.cells.length;i++) {
            var cell = firstRow.cells[i];
            var initialSortDir = cell.getAttribute("initialSortDir");
            if (initialSortDir != null) {
                column = i;
                dir = initialSortDir;
            }
        }
        if (dir == null) {
            return;
        }
    }
    // Work out a type for the column
    if (table.rows.length <= 1) return;
    var itm = extractData(table.rows[1].cells[column]).trim();
    var sortfn = ts_sort_caseinsensitive;
    if (itm.match(/^\d\d[\/-]\d\d[\/-]\d\d\d\d$/)) sortfn = ts_sort_date;
    if (itm.match(/^\d\d[\/-]\d\d[\/-]\d\d$/)) sortfn = ts_sort_date;
    if (itm.match(/^[�$]/)) sortfn = ts_sort_currency;
    if (itm.match(/^-?[\d\.]+$/)) sortfn = ts_sort_numeric;
    var firstRow = new Array();
    var newRows = new Array();
    for (i=0;i<table.rows[0].length;i++) { firstRow[i] = table.rows[0][i]; }
    for (j=1;j<table.rows.length;j++) { newRows[j-1] = table.rows[j]; }

    var sortfn2 = sortfn;
    if(dir === 'up') {
        // ascending
        sortfn2 = function(a,b){return -sortfn(a,b)};
    }
    newRows.sort(function(a,b) {
        return sortfn2(
            extractData(a.cells[column]),
            extractData(b.cells[column]));
    });
    for (var i=0;i<newRows.length;i++) {
        if (! Element.hasClassName(newRows[i], 'sortbottom'))
            table.tBodies[0].appendChild(newRows[i]);
    }
    for (var i=0;i<newRows.length;i++) {
        if (Element.hasClassName(newRows[i], 'sortbottom'))
            table.tBodies[0].appendChild(newRows[i]);
    }
}

function getParent(el, pTagName) {
	if (el == null) return null;
	else if (el.nodeType == 1 && el.tagName.toLowerCase() == pTagName.toLowerCase())	// Gecko bug, supposed to be uppercase
		return el;
	else
		return getParent(el.parentNode, pTagName);
}

function ts_sort_date(a,b) {
  function toDt(x) {
    // y2k notes: two digit years less than 50 are treated as 20XX, greater than 50 are treated as 19XX
    if (x.length == 10) {
        return x.substr(6,4)+x.substr(3,2)+x.substr(0,2);
    } else {
        yr = x.substr(6,2);
        if (parseInt(yr) < 50) { yr = '20'+yr; } else { yr = '19'+yr; }
        return yr+x.substr(3,2)+x.substr(0,2);
    }
  }

  var dt1 = toDt(a);
  var dt2 = toDt(b);

  if (dt1==dt2) return 0;
  if (dt1<dt2) return -1;
  return 1;
}

function ts_sort_currency(a,b) {
    a = a.replace(/[^0-9.]/g,'');
    b = b.replace(/[^0-9.]/g,'');
    return parseFloat(a) - parseFloat(b);
}

function ts_sort_numeric(a,b) {
    a = parseFloat(a);
    if (isNaN(a)) a = 0;
    b = parseFloat(b);
    if (isNaN(b)) b = 0;
    return a-b;
}

function ts_sort_caseinsensitive(a,b) {
    a = a.toLowerCase();
    b = b.toLowerCase();
    if (a==b) return 0;
    if (a<b) return -1;
    return 1;
}

function ts_sort_default(a,b) {
    if (a==b) return 0;
    if (a<b) return -1;
    return 1;
}

function ts_getIndexOfSortableTable(table){
    var allTables = document.getElementsByTagName("TABLE");
    var sortableTables = [];
    for (var i=0; i<allTables.length; i++) {
        if($(allTables[i]).hasClassName("sortable")){
            sortableTables.push(allTables[i]);
        }
    }
    return sortableTables.indexOf(table);
}
function ts_getStorageKey(table){
    var uri = document.location;
    var tableIndex = ts_getIndexOfSortableTable(table);
    return "ts_direction::" + uri + "::" + tableIndex;
}
function ts_saveDirection(table, columnIndex, direction){
    var key = ts_getStorageKey(table);
    ts_Storage.setItem(key, columnIndex + ":" + direction.id);
}
function ts_loadDirection(table){
    var key = ts_getStorageKey(table);
    if(ts_Storage.hasKey(key)){
        var val = ts_Storage.getItem(key);
        if(val){
            var vals = val.split(":");
            if(vals.length == 2) {
                var colIndex = parseInt(vals[0]);
                var direction = arrowTable[vals[1]];
                var col = table.rows[0].cells[colIndex];
                var anchor = col.firstChild;
                var arrow = anchor.lastChild;
                arrow.sortdir = direction;
                ts_resortTable(anchor);
            }
        }
    }
}

var ts_Storage;
try {
    ts_Storage = YAHOO.util.StorageManager.get(
        YAHOO.util.StorageEngineHTML5.ENGINE_NAME,
        YAHOO.util.StorageManager.LOCATION_SESSION,
        {
            order: [
                YAHOO.util.StorageEngineGears
            ]
        }
    );
} catch(e) {
    // no storage available
    ts_Storage = {
        setItem : function() {},
        getItem : function() { return null; },
        hasKey : function() { return false; }
    };
}
