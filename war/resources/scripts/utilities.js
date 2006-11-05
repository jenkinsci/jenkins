
UTILITIES_VERSION = 0.1;


/**
 * I'm nothing more than a collection of static utility methods for
 * doing various things.
 */
function Utilities() {}



/**
 * I remove all the children of the specified parent node.
 *
 * @param parent The node whose children should be removed
 */
Utilities.removeChildren = function(parent) {
	for (var i = parent.childNodes.length - 1; i >= 0; i--) {
		parent.removeChild(parent.childNodes[i]);
	}
}



/**
 * I return an object with an x and y fields, indicating the object's
 * offset from the top left corner of the document.  The 'offsets'
 * argument is optional; if not provided, one will be initialized and
 * used.  It is exposed as a parameter because it can be useful for
 * computing deltas.  The 'object' parameter can be either an actual
 * document element, or the ID of one.
 *
 * @param object The object to compute the offset of.  May be an object
 *		or the ID of one.
 * @param offsets The starting offsets to calculate from.  In almost
 *		all cases, this should be omitted.
 * @return An offsets object with x and y fields, indicating the
 *		computed offsets for the object.  If an offsets object is
 *		passed, that will be the object returned, though the values
 *		will have been changed.
 */
Utilities.getOffsets = function(object, offsets) {
	if (! offsets) {
		offsets = new Object();
		offsets.x = offsets.y = 0;
	}
	if (typeof object == "string")
		object = document.getElementById(object);
	offsets.x += object.offsetLeft;
	offsets.y += object.offsetTop;
	do {
		object = object.offsetParent;
		if (! object)
			break;
		offsets.x += object.offsetLeft;
		offsets.y += object.offsetTop;
	} while(object.tagName.toUpperCase() != "BODY");
	return offsets;
}



/**
 *
 */
Utilities.listAppend = function(list, value, delimiter) {
	if (typeof delimiter == "undefined")
		delimiter = ",";
	if (list == "")
		return value;
	else
		return list + delimiter + value;
}