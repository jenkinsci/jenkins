/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

if (typeof UTILITIES_VERSION == "undefined" || UTILITIES_VERSION < 0.1) {
	console.log("A suitable version of the Utilities class is not available");
}

COMBOBOX_VERSION = 0.1;

/*
Sample CSS for the combobox
	.comboBoxList {
		padding: 0px;
		border: 1px solid #999;
		background-color: #f7f7ff;
		overflow: visible;
	}
	.comboBoxItem {
		margin: 0px;
		padding: 2px 5px;
		background-color: inherit;
		cursor: default;
	}
	.comboBoxSelectedItem {
		background-color: #ddf;
	}
*/

/**
 * I create a new combobox, using the specified text input field and
 * population callback.  The item list is styled with three CSS
 * classes: comboBoxList, comboBoxItem, and comboBoxSelectedItem, which
 * are for the containing DIV, the individual item DIVs, and for the
 * currently selected item DIV.  Note that the selected item has both
 * the item and selectedItem classes applied.  Sample CSS is available
 * in a comment at the top of the implementation file.
 * 
 * The 'config' argument allows passing of additional parameters that
 * further govern the behaviour of the combo box.  Supported parameters
 * are listed here:
 * 	allowMultipleValues - whether the form field should allow multiple
 *		values to be provided.  Each individual value will get it's own
 *		separate dropdown with, so a field value such as "dog,ca" would
 *		operate as if the value were just "ca" (i.e. just "ca" would be
 *		passed to the callback, and a selection choice would only
 *		replace the "ca").  Defaults to false.
 *	valueDelimiter - if allowMultipleValues is set to true, this is the
 *		character used to delimit the values.  Defaults to a comma.
 *
 * @param idOrField The ID of the text field the combobox is based around, or the field itself.
 * @param callback The function to call when the typed value changes.
 *		The function will be passed the current value of the field, and
 *		must return an array of values to display in the dropdown.
 * @param config additional config parameters, as explained above.
 * @param config An object containing configuration parameters for the
 *		instance.
 */
function ComboBox(idOrField, callback, config) {
	var self = this;
	// instance variables
	this.config = config || new Object();
	this.callback = callback;
	this.availableItems = new Array();
	this.selectedItemIndex = -1;
	this.field = typeof idOrField == "string" ? document.getElementById(idOrField) : idOrField;
	if (typeof this.field == "undefined")
		alert("You have specified an invalid id for the field you want to turn into a combo box");
	this.dropdown = document.createElement("div");
	this.isDropdownShowing = false;
	this.oldonsubmit = null;
	
	// configure the dropdown div
	this.dropdown.className = "comboBoxList";
	document.body.appendChild(this.dropdown);
	this.dropdown.style.position = 'absolute';
    this.moveDropdown();
    this.hideDropdown();
	
	// initialize the field
	this.field.comboBox = this;
	this.field.oldValue = this.field.value;
	this.field.onkeyup = ComboBox.onKeyUp;
	this.field.moveCaretToEnd = function() {
		if (this.createTextRange) {
			var range = this.createTextRange();
			range.collapse(false);
			range.select();
		} else if (this.setSelectionRange) {
			this.focus();
			var length = this.value.length;
			this.setSelectionRange(length, length);
		}
	}
	this.field.onfocus = function() {
		this.comboBox.oldonsubmit = this.form.onsubmit;
		this.form.onsubmit = function() {
			if (self.isDropdownShowing) return false;
			if (self.oldonsubmit) self.oldonsubmit.call(this);
			return true;
		};
		// repopulate and display the dropdown
		this.comboBox.valueChanged();
	}
	this.field.onblur = function() {
		var cb = this.comboBox;
		this.hideTimeout = setTimeout(function() { cb.hideDropdown(); }, 100);
		this.form.onsubmit = cb.oldonsubmit;
	}
	
	// privileged methods
	this.getConfigParam = function(name, defVal) {
		return self.config[name] || defVal;
	}
}



/**
 * I am the onKeyDown listener that gets installed on the input field
 * that is the core of the ComboBox.  I handle action operations.
 *
 * @param e The event object on Mozilla browsers, null on IE
 */
ComboBox.onKeyDown = function(e) {
	if (!e) e = window.event;
	var capture = function() {
		e.cancelBubble = true;
		if (e.stopPropagation) e.stopPropagation();
	}
	switch (e.keyCode) {
		case 13: // enter
		case 9: // tab
			this.comboBox.chooseSelection();
			capture();
			return false;
		case 27: // escape
			this.comboBox.hideDropdown();
			capture();
		case 38: // up arrow
			this.comboBox.selectPrevious();
			capture();
			break;
		case 40: // down arrow
			this.comboBox.selectNext();
			capture();
			break;
	}
}



/**
 * I am the onKeyUp listener that gets installed on the input field
 * that is the core of the ComboBox.  I handle value-change operations.
 *
 * @param e The event object on Mozilla browsers, null on IE
 */
ComboBox.onKeyUp = function(e) {
	if (!e) e = window.event;
	var capture = function() {
		e.cancelBubble = true;
		if (e.stopPropagation) e.stopPropagation();
	}
	switch (e.keyCode) {
		case 38: // up arrow
		case 40: // down arrow
			this.moveCaretToEnd();
			capture();
			break;
		default:
			if (this.value != this.oldValue) {
				this.comboBox.valueChanged();
				this.oldValue = this.value;
			}
			capture();
	}
}



/**
 * I am called by the onKeyUp listener when the entered value changes,
 * and am responsible for invoking the application callback function
 * and repopulating the dropdown, if appropriate.  
 */
ComboBox.prototype.valueChanged = function() {
	var value = this.field.value;
	if (this.getConfigParam("allowMultipleValues", false)) {
		value = value.split(this.getConfigParam("valueDelimiter", ","));
		value = value[value.length - 1].replace(/^ +/, "").replace(/ +$/, "");
	}
	var a = this.callback(value, this);
	if (typeof a == "undefined") // to catch null returns
		return;
	this.setItems(a);
}



/**
 * I can be called at any time with a new set of items to display in
 * the dropdown.
 *
 * @param items The array of items that should be used for the dropdown
 *		values.
 */
ComboBox.prototype.setItems = function(items) {
	if (typeof items != "object") {
		alert("setItems wasn't passed a valid array: " + typeof a);
		return;
	}
	this.availableItems = items;
	this.populateDropdown();
}



/**
 * I am called to repopulate the dropdown.  There should never be a
 * need to invoke me externally.
 */
ComboBox.prototype.populateDropdown = function() {
	if (this.availableItems.length > 0) {
		Utilities.removeChildren(this.dropdown);
		for (var i = 0; i < this.availableItems.length; i++) {
			var item = document.createElement("div");
			item.className = "comboBoxItem";
			item.innerText = this.availableItems[i];
			item.id = "item_" + this.availableItems[i];
			item.comboBox = this;
			item.comboBoxIndex = i;
			item.onmouseover = function() {this.comboBox.select(this.comboBoxIndex);};
			item.onmousedown = function() {this.comboBox.choose(this.comboBoxIndex);};
			this.dropdown.appendChild(item);
		}
		this.selectedItemIndex = 0;
		this.updateSelection();
		this.showDropdown();
	} else {
		this.selectedItemIndex = -1;
		this.hideDropdown();
	}
}



/**
 * I am called by a mouse listener on the dropdown items to choose a
 * specific item straight away.
 *
 * @param index The index of the item to choose
 */
ComboBox.prototype.choose = function(index) {
	if (this.select(index))
		this.chooseSelection();
}



/**
 * I am called by the onKeyUp listener to indicate that the user wants
 * to use the current selection as the new value of the field.
 */
ComboBox.prototype.chooseSelection = function() {
	var i = this.selectedItemIndex;
	var a = this.availableItems;
	if (i >= 0 && i < a.length) {
		var valueToAdd = a[i].replace(/<[^>]+>/g, "");
		if (this.getConfigParam("allowMultipleValues", false)) {
			var currentValue = "";
			var delim = this.getConfigParam("valueDelimiter", ",");
			values = this.field.value.split(delim);
			for (var j = 0; j < values.length - 1; j++) {
				currentValue = Utilities.listAppend(currentValue, values[j], delim);
			}
			this.field.value = Utilities.listAppend(currentValue, valueToAdd, delim);
		} else {
			this.field.value = valueToAdd;
		}

		this.field.oldValue = this.field.value;
		this.field.focus();
		this.field.moveCaretToEnd();
		this.hideDropdown();
	}
}



/**
 * I am called by a mouse listener on the dropdown items to select a
 * specific item straight away.
 *
 * @param index The index of the item to select
 * @return whether the selection happened (the index was valid)
 */
ComboBox.prototype.select = function(index) {
	if (index < 0 || index >= this.availableItems.length)
		return false;
	this.selectedItemIndex = index;
	this.updateSelection();
	return true;
}



/**
 * I am called by the onKeyUp listener to indicate that the user wants
 * to select the next option in the dropdown.
 */
ComboBox.prototype.selectNext = function() {
	if (this.selectedItemIndex >= this.availableItems.length - 1)
		return false;
	this.selectedItemIndex++;
	this.updateSelection();
	return true;
}



/**
 * I am called by the onKeyUp listener to indicate that the user wants
 * to select the previous option in the dropdown.
 */
ComboBox.prototype.selectPrevious = function() {
	if (this.selectedItemIndex <= 0)
		return false;
	this.selectedItemIndex--;
	this.updateSelection();
}



/**
 * I show the dropdown DIV.
 */
ComboBox.prototype.showDropdown = function() {
    this.moveDropdown();
    clearTimeout(this.field.hideTimeout);
	this.dropdown.style.display = 'block';
	this.field.onkeydown = ComboBox.onKeyDown;
	this.isDropdownShowing = true;
}



/**
 * I hide the dropdown DIV.
 */
ComboBox.prototype.hideDropdown = function() {
	var self = this;
	setTimeout(function() {self.isDropdownShowing = false;}, 100);
	this.field.onkeydown = null;
	this.dropdown.style.display = 'none';
}

ComboBox.prototype.moveDropdown = function() {
    var offsets = Utilities.getOffsets(this.field);
    this.dropdown.style.top = offsets.y + (this.field.offsetHeight ? this.field.offsetHeight : 22) + "px";
    this.dropdown.style.left = offsets.x + "px";
    this.dropdown.style.width = (this.field.offsetWidth ? this.field.offsetWidth : 100) + "px"
}



/**
 * I update the dropdown so that the display reflects the internally
 * selected item,
 */
ComboBox.prototype.updateSelection = function() {
	for (var i = 0; i < this.dropdown.childNodes.length; i++) {
		if (i == this.selectedItemIndex) {
			this.dropdown.childNodes[i].className += " comboBoxSelectedItem";
		} else {
			this.dropdown.childNodes[i].className = this.dropdown.childNodes[i].className.replace(/ *comboBoxSelectedItem */g, "");
		}
	}
}
