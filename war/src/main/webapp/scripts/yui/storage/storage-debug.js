/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
/**
 * The Storage module manages client-side data storage.
 * @module Storage
 */

(function() {

	// internal shorthand
var Y = YAHOO,
	Util = Y.util,
	Lang = Y.lang,
	_logOverwriteError,
    Storage,

	RX_TYPE = /^type=(\w+)/i,
	RX_VALUE = /&value=(.*)/i;

if (! Util.Storage) {
	_logOverwriteError = function(fxName) {
		Y.log('Exception in YAHOO.util.Storage.?? - must be extended by a storage engine'.replace('??', fxName).replace('??', this.getName ? this.getName() : 'Unknown'), 'error');
	};

	/**
	 * The Storage class is an HTML 5 storage API clone, used to wrap individual storage implementations with a common API.
	 * @class Storage
	 * @namespace YAHOO.util
	 * @constructor
	 * @param sLocation {String} Required. The storage location.
	 * @parm sName {String} Required. The engine name.
	 * @param oConf {Object} Required. A configuration object.
	 */
	Storage = function(sLocation, sName, oConf) {
		var that = this;
		Y.env._id_counter += 1;

		// protected variables
		that._cfg = Lang.isObject(oConf) ? oConf : {};
		that._location = sLocation;
		that._name = sName;
		that.isReady = false;

		// public variables
		that.createEvent(Storage.CE_READY, {scope: that, fireOnce: true});
		that.createEvent(Storage.CE_CHANGE, {scope: that});
		
		that.subscribe(Storage.CE_READY, function() {
			that.isReady = true;
		});
	};

    Storage.CE_READY = 'YUIStorageReady';
    Storage.CE_CHANGE = 'YUIStorageChange';

	Storage.prototype = {

		/**
		 * The event name for when the storage item is ready.
		 * @property CE_READY
		 * @type {String}
		 * @public
		 */
		CE_READY: Storage.CE_READY,

		/**
		 * The event name for when the storage item has changed.
		 * @property CE_CHANGE
		 * @type {String}
		 * @public
		 */
		CE_CHANGE: Storage.CE_CHANGE,

		/**
		 * The configuration of the engine.
		 * @property _cfg
		 * @type {Object}
		 * @protected
		 */
		_cfg: '',

		/**
		 * The name of this engine.
		 * @property _name
		 * @type {String}
		 * @protected
		 */
		_name: '',

		/**
		 * The location for this instance.
		 * @property _location
		 * @type {String}
		 * @protected
		 */
		_location: '',

		/**
		 * The current length of the keys.
		 * @property length
		 * @type {Number}
		 * @public
		 */
		length: 0,

		/**
		 * This engine singleton has been initialized already.
		 * @property isReady
		 * @type {String}
		 * @protected
		 */
		isReady: false,

		/**
		 * Clears any existing key/value pairs.
		 * @method clear
		 * @public
		 */
		clear: function() {
			this._clear();
			this.length = 0;
		},

		/**
		 * Fetches the data stored and the provided key.
		 * @method getItem
		 * @param sKey {String} Required. The key used to reference this value (DOMString in HTML 5 spec).
		 * @return {String|NULL} The value stored at the provided key (DOMString in HTML 5 spec).
		 * @public
		 */
		getItem: function(sKey) {
			Y.log("Fetching item at  " + sKey);
			var oItem = this._getItem(sKey);
			return Lang.isValue(oItem) ? this._getValue(oItem) : null; // required by HTML 5 spec
		},

		/**
		 * Fetches the storage object's name; should be overwritten by storage engine.
		 * @method getName
		 * @return {String} The name of the data storage object.
		 * @public
		 */
		getName: function() {return this._name;},

		/**
		 * Tests if the key has been set (not in HTML 5 spec); should be overwritten by storage engine.
		 * @method hasKey
		 * @param sKey {String} Required. The key to search for.
		 * @return {Boolean} True when key has been set.
		 * @public
		 */
		hasKey: function(sKey) {
			return Lang.isString(sKey) && this._hasKey(sKey);
		},

		/**
		 * Retrieve the key stored at the provided index; should be overwritten by storage engine.
		 * @method key
		 * @param nIndex {Number} Required. The index to retrieve (unsigned long in HTML 5 spec).
		 * @return {String} Required. The key at the provided index (DOMString in HTML 5 spec).
		 * @public
		 */
		key: function(nIndex) {
			Y.log("Fetching key at " + nIndex);

			if (Lang.isNumber(nIndex) && -1 < nIndex && this.length > nIndex) {
				var value = this._key(nIndex);
				if (value) {return value;}
			}

			// this is thrown according to the HTML5 spec
			throw('INDEX_SIZE_ERR - Storage.setItem - The provided index (' + nIndex + ') is not available');
		},

		/**
		 * Remove an item from the data storage.
		 * @method removeItem
		 * @param sKey {String} Required. The key to remove (DOMString in HTML 5 spec).
		 * @public
		 */
		removeItem: function(sKey) {
			Y.log("removing " + sKey);
            var that = this,
                oOldValue;
			
			if (that.hasKey(sKey)) {
                oOldValue = that._getItem(sKey);
                if (! oOldValue) {oOldValue = null;}
                that._removeItem(sKey);
				that.fireEvent(Storage.CE_CHANGE, new Util.StorageEvent(that, sKey, oOldValue, null, Util.StorageEvent.TYPE_REMOVE_ITEM));
			}
			else {
				// HTML 5 spec says to do nothing
			}
		},

		/**
		 * Adds an item to the data storage.
		 * @method setItem
		 * @param sKey {String} Required. The key used to reference this value (DOMString in HTML 5 spec).
		 * @param oData {Object} Required. The data to store at key (DOMString in HTML 5 spec).
		 * @public
		 * @throws QUOTA_EXCEEDED_ERROR
		 */
		setItem: function(sKey, oData) {
			Y.log("SETTING " + oData + " to " + sKey);
			
			if (Lang.isString(sKey)) {
				var that = this,
                    oOldValue = that._getItem(sKey);

				if (! oOldValue) {oOldValue = null;}

				if (that._setItem(sKey, that._createValue(oData))) {
					that.fireEvent(Storage.CE_CHANGE, new Util.StorageEvent(that, sKey, oOldValue, oData,
                            that.hasKey(sKey) ? Util.StorageEvent.TYPE_UPDATE_ITEM : Util.StorageEvent.TYPE_ADD_ITEM));
				}
				else {
					// that is thrown according to the HTML5 spec
					throw('QUOTA_EXCEEDED_ERROR - Storage.setItem - The choosen storage method (' +
						  that.getName() + ') has exceeded capacity');
				}
			}
			else {
				// HTML 5 spec says to do nothing
			}
		},

		/**
		 * Implementation of the clear login; should be overwritten by storage engine.
		 * @method _clear
		 * @protected
		 */
		_clear: function() {
			_logOverwriteError('_clear');
			return '';
		},

		/**
		 * Converts the object into a string, with meta data (type), so it can be restored later.
		 * @method _createValue
		 * @param o {Object} Required. An object to store.
		 * @protected
		 */
		_createValue: function(o) {
			var sType = (Lang.isNull(o) || Lang.isUndefined(o)) ? ('' + o) : typeof o;
			return 'type=' + sType + '&value=' + encodeURIComponent('' + o);
		},

		/**
		 * Implementation of the getItem login; should be overwritten by storage engine.
		 * @method _getItem
		 * @param sKey {String} Required. The key used to reference this value.
		 * @return {String|NULL} The value stored at the provided key.
		 * @protected
		 */
		_getItem: function(sKey) {
			_logOverwriteError('_getItem');
			return '';
		},

		/**
		 * Converts the stored value into its appropriate type.
		 * @method _getValue
		 * @param s {String} Required. The stored value.
		 * @protected
		 */
		_getValue: function(s) {
			var sType = s.match(RX_TYPE)[1],
				sValue = s.match(RX_VALUE)[1];

			switch (sType) {
				case 'boolean': return 'true' == sValue;
				case 'number': return parseFloat(sValue);
				case 'null': return null;
				default: return decodeURIComponent(sValue);
			}
		},

		/**
		 * Implementation of the key logic; should be overwritten by storage engine.
		 * @method _key
		 * @param nIndex {Number} Required. The index to retrieve (unsigned long in HTML 5 spec).
		 * @return {String|NULL} Required. The key at the provided index (DOMString in HTML 5 spec).
		 * @protected
		 */
		_key: function(nIndex) {
			_logOverwriteError('_key');
			return '';
		},

		/*
		 * Implementation to fetch evaluate the existence of a key.
		 */
		_hasKey: function(sKey) {
			return null !== this._getItem(sKey);
		},

		/**
		 * Implementation of the removeItem login; should be overwritten by storage engine.
		 * @method _removeItem
		 * @param sKey {String} Required. The key to remove.
		 * @protected
		 */
		_removeItem: function(sKey) {
			_logOverwriteError('_removeItem');
			return '';
		},

		/**
		 * Implementation of the setItem login; should be overwritten by storage engine.
		 * @method _setItem
		 * @param sKey {String} Required. The key used to reference this value.
		 * @param oData {Object} Required. The data to storage at key.
		 * @return {Boolean} True when successful, false when size QUOTA exceeded.
		 * @protected
		 */
		_setItem: function(sKey, oData) {
			_logOverwriteError('_setItem');
			return '';
		}
	};

	Lang.augmentProto(Storage, Util.EventProvider);

    Util.Storage = Storage;
}

}());
/**
 * The StorageManager class is a singleton that registers DataStorage objects and returns instances of those objects.
 * @class StorageManager
 * @namespace YAHOO.util
 * @static
 */
(function() {
	// internal shorthand
var Util = YAHOO.util,
	Lang = YAHOO.lang,

	// private variables
	_locationEngineMap = {}, // cached engines
	_registeredEngineSet = [], // set of available engines
	_registeredEngineMap = {}, // map of available engines
	
	/**
	 * Fetches a storage constructor if it is available, otherwise returns NULL.
	 * @method _getClass
	 * @param fnClass {Function} Required. The storage constructor to test.
	 * @return {Function} An available storage constructor or NULL.
	 * @private
	 */
	_getClass = function(fnClass) {
		return (fnClass && fnClass.isAvailable()) ? fnClass : null;
	},

	/**
	 * Fetches the storage engine from the cache, or creates and caches it.
	 * @method _getStorageEngine
	 * @param sLocation {String} Required. The location to store.
	 * @param fnClass {Function} Required. A pointer to the engineType Class.
	 * @param oConf {Object} Optional. Additional configuration for the data source engine.
	 * @private
	 */
	_getStorageEngine = function(sLocation, fnClass, oConf) {
		var engine = _locationEngineMap[sLocation + fnClass.ENGINE_NAME];

		if (! engine) {
			engine = new fnClass(sLocation, oConf);
			_locationEngineMap[sLocation + fnClass.ENGINE_NAME] = engine;
		}

		return engine;
	},

	/**
	 * Ensures that the location is valid before returning it or a default value.
	 * @method _getValidLocation
	 * @param sLocation {String} Required. The location to evaluate.
	 * @private
	 */
	_getValidLocation = function(sLocation) {
		switch (sLocation) {
			case Util.StorageManager.LOCATION_LOCAL:
			case Util.StorageManager.LOCATION_SESSION:
				return sLocation;

			default: return Util.StorageManager.LOCATION_SESSION;
		}
	};

	// public namespace
	Util.StorageManager = {

        /**
         * The storage location - session; data cleared at the end of a user's session.
         * @property LOCATION_SESSION
         * @type {String}
         * @static
         */
		LOCATION_SESSION: 'sessionStorage',

        /**
         * The storage location - local; data cleared on demand.
         * @property LOCATION_LOCAL
         * @type {String}
         * @static
         */
		LOCATION_LOCAL: 'localStorage',

		/**
		 * Fetches the desired engine type or first available engine type.
		 * @method get
		 * @param engineType {String} Optional. The engine type, see engines.
		 * @param sLocation {String} Optional. The storage location - LOCATION_SESSION & LOCATION_LOCAL; default is LOCAL.
		 * @param oConf {Object} Optional. Additional configuration for the getting the storage engine.
		 * {
		 * 	engine: {Object} configuration parameters for the desired engine
		 * 	force: {Boolean} force the <code>engineType</code> or fail
		 * 	order: {Array} an array of storage engine names; the desired order to try engines}
		 * }
		 * @static
		 */
		get: function(engineType, sLocation, oConf) {
			var oCfg = Lang.isObject(oConf) ? oConf : {},
				fnClass = _getClass(_registeredEngineMap[engineType]),
                i , j;

			if (! fnClass && ! oCfg.force) {
				if (oCfg.order) {
					j = oCfg.order.length;

					for (i = 0; i < j && ! fnClass; i += 1) {
						fnClass = _getClass(oCfg.order[i]);
					}
				}

				if (! fnClass) {
					j = _registeredEngineSet.length;

					for (i = 0; i < j && ! fnClass; i += 1) {
						fnClass = _getClass(_registeredEngineSet[i]);
					}
				}
			}

			if (fnClass) {
				return _getStorageEngine(_getValidLocation(sLocation), fnClass, oCfg.engine);
			}

			throw('YAHOO.util.StorageManager.get - No engine available, please include an engine before calling this function.');
		},

        /*
         * Estimates the size of the string using 1 byte for each alpha-numeric character and 3 for each non-alpha-numeric character.
         * @method getByteSize
         * @param s {String} Required. The string to evaulate.
         * @return {Number} The estimated string size.
         * @private
         */
        getByteSize: function(s) {
			return encodeURIComponent('' + s).length;
        },

		/**
		 * Registers a engineType Class with the StorageManager singleton; first in is the first out.
		 * @method register
		 * @param engineConstructor {Function} Required. The engine constructor function, see engines.
		 * @return {Boolean} When successfully registered.
		 * @static
		 */
		register: function(engineConstructor) {
			if (Lang.isFunction(engineConstructor) && Lang.isFunction(engineConstructor.isAvailable) &&
                    Lang.isString(engineConstructor.ENGINE_NAME)) {
				_registeredEngineMap[engineConstructor.ENGINE_NAME] = engineConstructor;
				_registeredEngineSet.push(engineConstructor);
				return true;
			}

			return false;
		}
	};

	YAHOO.register("StorageManager", Util.SWFStore, {version: "2.9.0", build: "2800"});
}());
(function() {

/**
 * The StorageEvent class manages the storage events by emulating the HTML 5 implementation.
 * @namespace YAHOO.util
 * @class StorageEvent
 * @constructor
 * @param oStorageArea {Object} Required. The Storage object that was affected.
 * @param sKey {String} Required. The key being changed; DOMString in HTML 5 spec.
 * @param oOldValue {Mixed} Required. The old value of the key being changed; DOMString in HTML 5 spec.
 * @param oNewValue {Mixed} Required. The new value of the key being changed; DOMString in HTML 5 spec.
 * @param sType {String} Required. The storage event type.
 */
function StorageEvent(oStorageArea, sKey, oOldValue, oNewValue, sType) {
	this.key = sKey;
	this.oldValue = oOldValue;
	this.newValue = oNewValue;
	this.url = window.location.href;
	this.window = window; // todo: think about the CAJA and innocent code
	this.storageArea = oStorageArea;
	this.type = sType;
}

YAHOO.lang.augmentObject(StorageEvent, {
	TYPE_ADD_ITEM: 'addItem',
	TYPE_REMOVE_ITEM: 'removeItem',
	TYPE_UPDATE_ITEM: 'updateItem'
});

StorageEvent.prototype = {

    /**
     * The 'key' attribute represents the key being changed.
     * @property key
     * @type {String}
     * @static
     * @readonly
     */
    key: null,

    /**
     * The 'newValue' attribute represents the new value of the key being changed.
     * @property newValue
     * @type {Mixed}
     * @static
     * @readonly
     */
    newValue: null,

    /**
     * The 'oldValue' attribute represents the old value of the key being changed.
     * @property oldValue
     * @type {Mixed}
     * @static
     * @readonly
     */
    oldValue: null,

    /**
     * The 'source' attribute represents the WindowProxy object of the browsing context of the document whose key changed.
     * @property source
     * @type {Object}
     * @static
     * @readonly
     */
    source: null,

    /**
     * The 'storageArea' attribute represents the Storage object that was affected.
     * @property storageArea
     * @type {Object}
     * @static
     * @readonly
     */
    storageArea: null,

    /**
     * The 'type' attribute represents the Storage event type.
     * @property type
     * @type {Object}
     * @static
     * @readonly
     */
    type: null,

    /**
     * The 'url' attribute represents the address of the document whose key changed.
     * @property url
     * @type {String}
     * @static
     * @readonly
     */
    url: null
};

YAHOO.util.StorageEvent = StorageEvent;
	
}());
(function() {
var Util = YAHOO.util;

	/**
	 * The StorageEngineKeyed class implements the interface necessary for managing keys.
	 * @namespace YAHOO.util
	 * @class StorageEngineKeyed
	 * @constructor
	 * @extend YAHOO.util.Storage
	 */
	Util.StorageEngineKeyed = function() {
		Util.StorageEngineKeyed.superclass.constructor.apply(this, arguments);
		this._keys = [];
		this._keyMap = {};
	};

	YAHOO.lang.extend(Util.StorageEngineKeyed, Util.Storage, {

		/**
		 * A collection of keys applicable to the current location. This should never be edited by the developer.
		 * @property _keys
		 * @type {Array}
		 * @protected
		 */
		_keys: null,

		/**
		 * A map of keys to their applicable position in keys array. This should never be edited by the developer.
		 * @property _keyMap
		 * @type {Object}
		 * @protected
		 */
		_keyMap: null,

		/**
		 * Adds the key to the set.
		 * @method _addKey
		 * @param sKey {String} Required. The key to evaluate.
		 * @protected
		 */
		_addKey: function(sKey) {
		    if (!this._keyMap.hasOwnProperty(sKey)) {
    			this._keys.push(sKey);
			    this._keyMap[sKey] = this.length;
			    this.length = this._keys.length;
			}
		},

		/*
		 * Implementation to clear the values from the storage engine.
		 * @see YAHOO.util.Storage._clear
		 */
		_clear: function() {
			this._keys = [];
			this.length = 0;
		},

		/**
		 * Evaluates if a key exists in the keys array; indexOf does not work in all flavors of IE.
		 * @method _indexOfKey
		 * @param sKey {String} Required. The key to evaluate.
		 * @protected
		 */
		_indexOfKey: function(sKey) {
			var i = this._keyMap[sKey];
			return undefined === i ? -1 : i;
		},

		/*
		 * Implementation to fetch a key from the storage engine.
		 * @see YAHOO.util.Storage.key
		 */
		_key: function(nIndex) {return this._keys[nIndex];},

		/**
		 * Removes a key from the keys array.
		 * @method _removeItem
		 * @param sKey {String} Required. The key to remove.
		 * @protected
		 */
		_removeItem: function(sKey) {
			var that = this,
                j = that._indexOfKey(sKey),
				rest = that._keys.slice(j + 1),
                k;

			delete that._keyMap[sKey];

			// update values in keymap that are greater than current position
			for (k in that._keyMap) {
				if (j < that._keyMap[k]) {
					that._keyMap[k] -= 1;
				}
			}
			
			that._keys.length = j;
			that._keys = that._keys.concat(rest);
			that.length = that._keys.length;
		}
	});
}());
/*
 * HTML limitations:
 *  - 5MB in FF and Safari, 10MB in IE 8
 *  - only FF 3.5 recovers session storage after a browser crash
 *
 * Thoughts:
 *  - how can we not use cookies to handle session
 */
(function() {
	// internal shorthand
var Util = YAHOO.util,
	Lang = YAHOO.lang,

	/*
	 * Required for IE 8 to make synchronous.
	 */
	_beginTransaction = function(driver) {
		driver.begin();
	},

	/*
	 * Required for IE 8 to make synchronous.
	 */
	_commitTransaction = function(driver) {
		driver.commit();
	},

	/**
	 * The StorageEngineHTML5 class implements the HTML5 storage engine.
	 * @namespace YAHOO.util
	 * @class StorageEngineHTML5
	 * @constructor
	 * @extend YAHOO.util.Storage
	 * @param sLocation {String} Required. The storage location.
	 * @param oConf {Object} Required. A configuration object.
	 */
	StorageEngineHTML5 = function(sLocation, oConf) {
		var that = this,
            oDriver = window[sLocation];
        
		StorageEngineHTML5.superclass.constructor.call(that, sLocation, StorageEngineHTML5.ENGINE_NAME, oConf);// not set, are cookies available

		// simplifieds the begin/commit functions, if not using IE; this provides a massive performance boost
		if (! oDriver.begin) {_beginTransaction = function() {}; }
		if (! oDriver.commit) {_commitTransaction = function() {}; }

		that.length = oDriver.length;
        that._driver = oDriver;
        that.fireEvent(Util.Storage.CE_READY);
	};

	Lang.extend(StorageEngineHTML5, Util.Storage, {

		_driver: null,

		/*
		 * Implementation to clear the values from the storage engine.
		 * @see YAHOO.util.Storage._clear
		 */
		_clear: function() {
			var that = this, i, sKey;

			if (that._driver.clear) {
				that._driver.clear();
			}
			// for FF 3, fixed in FF 3.5
			else {
				for (i = that.length; 0 <= i; i -= 1) {
					sKey = that._key(i);
					that._removeItem(sKey);
				}
			}
		},

		/*
		 * Implementation to fetch an item from the storage engine.
		 * @see YAHOO.util.Storage._getItem
		 */
		_getItem: function(sKey) {
			var o = this._driver.getItem(sKey);
			return Lang.isObject(o) ? o.value : o; // for FF 3, fixed in FF 3.5
		},

		/*
		 * Implementation to fetch a key from the storage engine.
		 * @see YAHOO.util.Storage._key
		 */
		_key: function(nIndex) {return this._driver.key(nIndex);},

		/*
		 * Implementation to remove an item from the storage engine.
		 * @see YAHOO.util.Storage._removeItem
		 */
		_removeItem: function(sKey) {
			var oDriver = this._driver;
			_beginTransaction(oDriver);
			oDriver.removeItem(sKey);
			_commitTransaction(oDriver);
			this.length = oDriver.length;
		},

		/*
		 * Implementation to remove an item from the storage engine.
		 * @see YAHOO.util.Storage._setItem
		 */
		_setItem: function(sKey, oValue) {
			var oDriver = this._driver;

			try {
				_beginTransaction(oDriver);
				oDriver.setItem(sKey, oValue);
				_commitTransaction(oDriver);
				this.length = oDriver.length;
				return true;
			}
			catch (e) {
				return false;
			}
		}
	}, true);

	StorageEngineHTML5.ENGINE_NAME = 'html5';
    
	StorageEngineHTML5.isAvailable = function() {
        try {
            return ('localStorage' in window) && window['localStorage'] !== null &&
                    ('sessionStorage' in window) && window['sessionStorage'] !== null;
        }
        catch (e) {
            /*
                In FireFox and maybe other browsers, you can disable storage in the configuration settings, which
                will cause an error to be thrown instead of evaluating the simple if/else statement.
             */
            return false;
        }
    };

    Util.StorageManager.register(StorageEngineHTML5);
	Util.StorageEngineHTML5 = StorageEngineHTML5;
}());
/*
 * Gears limitation:
 *  - SQLite limitations - http://www.sqlite.org/limits.html
 *  - DB Best Practices - http://code.google.com/apis/gears/gears_faq.html#bestPracticeDB
 * 	- the user must approve before gears can be used
 *  - each SQL query has a limited number of characters (9948 bytes), data will need to be spread across rows
 *  - no query should insert or update more than 9948 bytes of data in a single statement or GEARs will throw:
 *  	[Exception... "'Error: SQL statement is too long.' when calling method: [nsIDOMEventListener::handleEvent]" nsresult: "0x8057001c (NS_ERROR_XPC_JS_THREW_JS_OBJECT)" location: "<unknown>" data: no]
 *
 * Thoughts:
 *  - we may want to implement additional functions for the gears only implementation
 *  - how can we not use cookies to handle session location
 */
(function() {
	// internal shorthand
var Util = YAHOO.util,
	Lang = YAHOO.lang,
	SQL_STMT_LIMIT = 9948,
	TABLE_NAME = 'YUIStorageEngine',

	// local variables
	_driver = null,

	eURI = encodeURIComponent,
	dURI = decodeURIComponent,

	/**
	 * The StorageEngineGears class implements the Google Gears storage engine.
	 * @namespace YAHOO.util
	 * @class StorageEngineGears
	 * @constructor
	 * @extend YAHOO.util.Storage
	 * @param sLocation {String} Required. The storage location.
	 * @param oConf {Object} Required. A configuration object.
	 */
	StorageEngineGears = function(sLocation, oConf) {
		var that = this,
            keyMap = {},
            isSessionStorage, sessionKey, rs;
        
		StorageEngineGears.superclass.constructor.call(that, sLocation, StorageEngineGears.ENGINE_NAME, oConf);

		if (! _driver) {
			// create the database
			_driver = google.gears.factory.create(StorageEngineGears.GEARS);
      // the replace regex fixes http://yuilibrary.com/projects/yui2/ticket/2529411, all ascii characters are allowede except / : * ? " < > | ; ,
			_driver.open(window.location.host.replace(/[\/\:\*\?"\<\>\|;,]/g, '') + '-' + StorageEngineGears.DATABASE);
			_driver.execute('CREATE TABLE IF NOT EXISTS ' + TABLE_NAME + ' (key TEXT, location TEXT, value TEXT)');
		}

		isSessionStorage = Util.StorageManager.LOCATION_SESSION === that._location;
		sessionKey = Util.Cookie.get('sessionKey' + StorageEngineGears.ENGINE_NAME);

		if (! sessionKey) {
			_driver.execute('BEGIN');
			_driver.execute('DELETE FROM ' + TABLE_NAME + ' WHERE location="' + eURI(Util.StorageManager.LOCATION_SESSION) + '"');
			_driver.execute('COMMIT');
		}

		rs = _driver.execute('SELECT key FROM ' + TABLE_NAME + ' WHERE location="' + eURI(that._location) + '"');
		keyMap = {};
	
		try {
			// iterate on the rows and map the keys
			while (rs.isValidRow()) {
				var fld = dURI(rs.field(0));

				if (! keyMap[fld]) {
					keyMap[fld] = true;
					that._addKey(fld);
				}

				rs.next();
			}
		} finally {
			rs.close();
		}

		// this is session storage, ensure that the session key is set
		if (isSessionStorage) {
			Util.Cookie.set('sessionKey' + StorageEngineGears.ENGINE_NAME, true);
		}

        that.fireEvent(Util.Storage.CE_READY);
	};

	Lang.extend(StorageEngineGears, Util.StorageEngineKeyed, {

		/*
		 * Implementation to clear the values from the storage engine.
		 * @see YAHOO.util.Storage._clear
		 */
		_clear: function() {
			StorageEngineGears.superclass._clear.call(this);
			_driver.execute('BEGIN');
			_driver.execute('DELETE FROM ' + TABLE_NAME + ' WHERE location="' + eURI(this._location) + '"');
			_driver.execute('COMMIT');
		},

		/*
		 * Implementation to fetch an item from the storage engine.
		 * @see YAHOO.util.Storage._getItem
		 */
		_getItem: function(sKey) {
			var rs = _driver.execute('SELECT value FROM ' + TABLE_NAME + ' WHERE key="' + eURI(sKey) + '" AND location="' + eURI(this._location) + '"'),
				value = '';

			try {
				while (rs.isValidRow()) {
					value += rs.field(0);
					rs.next();
				}
			} finally {
				rs.close();
			}

			return value ? dURI(value) : null;
		},

		/*
		 * Implementation to remove an item from the storage engine.
		 * @see YAHOO.util.Storage._removeItem
		 */
		_removeItem: function(sKey) {
			YAHOO.log("removing gears key: " + sKey);
			StorageEngineGears.superclass._removeItem.call(this, sKey);
			_driver.execute('BEGIN');
			_driver.execute('DELETE FROM ' + TABLE_NAME + ' WHERE key="' + eURI(sKey) + '" AND location="' + eURI(this._location) + '"');
			_driver.execute('COMMIT');
		},

		/*
		 * Implementation to remove an item from the storage engine.
		 * @see YAHOO.util.Storage._setItem
		 */
		_setItem: function(sKey, oData) {
			YAHOO.log("SETTING " + oData + " to " + sKey);

			this._addKey(sKey);

			var sEscapedKey = eURI(sKey),
				sEscapedLocation = eURI(this._location),
				sEscapedValue = eURI(oData), // escaped twice, maybe not necessary
				aValues = [],
				nLen = SQL_STMT_LIMIT - (sEscapedKey + sEscapedLocation).length,
                i=0, j;

			// the length of the value exceeds the available space
			if (nLen < sEscapedValue.length) {
				for (j = sEscapedValue.length; i < j; i += nLen) {
					aValues.push(sEscapedValue.substr(i, nLen));
				}
			} else {
				aValues.push(sEscapedValue);
			}

			// Google recommends using INSERT instead of update, because it is faster
			_driver.execute('BEGIN');
			_driver.execute('DELETE FROM ' + TABLE_NAME + ' WHERE key="' + sEscapedKey + '" AND location="' + sEscapedLocation + '"');
			for (i = 0, j = aValues.length; i < j; i += 1) {
				_driver.execute('INSERT INTO ' + TABLE_NAME + ' VALUES ("' + sEscapedKey + '", "' + sEscapedLocation + '", "' + aValues[i] + '")');
			}
			_driver.execute('COMMIT');
			
			return true;
		}
	});

	// releases the engine when the page unloads
	Util.Event.on('unload', function() {
		if (_driver) {_driver.close();}
	});

    StorageEngineGears.ENGINE_NAME = 'gears';
	StorageEngineGears.GEARS = 'beta.database';
	StorageEngineGears.DATABASE = 'yui.database';

	StorageEngineGears.isAvailable = function() {
		if (('google' in window) && ('gears' in window.google)) {
			try {
				// this will throw an exception if the user denies gears
				google.gears.factory.create(StorageEngineGears.GEARS);
				return true;
			}
			catch (e) {
				// no need to do anything
			}
		}

		return false;
	};

    Util.StorageManager.register(StorageEngineGears);
	Util.StorageEngineGears = StorageEngineGears;
}());
/*
 * SWF limitation:
 * 	- only 100,000 bytes of data may be stored this way
 *  - data is publicly available on user machine
 *
 * Thoughts:
 *  - data can be shared across browsers
 *  - how can we not use cookies to handle session location
 */
(function() {
	// internal shorthand
var Y = YAHOO,
    Util = Y.util,
	Lang = Y.lang,
	Dom = Util.Dom,
	StorageManager = Util.StorageManager,
	
	/*
	 * The minimum width required to be able to display the settings panel within the SWF.
	 */	
	MINIMUM_WIDTH = 215,

	/*
	 * The minimum height required to be able to display the settings panel within the SWF.
	 */	
	MINIMUM_HEIGHT = 138,

	RX_STORAGE_PREFIX = new RegExp('^(' + StorageManager.LOCATION_SESSION + '|' + StorageManager.LOCATION_LOCAL + ')'),

	// local variables
	_driver = null,

	/*
	 * Creates a location bound key.
	 */
	_getKey = function(that, sKey) {
		return that._location + sKey;
	},

	/*
	 * Initializes the engine, if it isn't already initialized.
	 */
	_initDriver = function(oCfg) {
		if (! _driver) {
			if (! Lang.isString(oCfg.swfURL)) {oCfg.swfURL = StorageEngineSWF.SWFURL;}
			if (! oCfg.containerID) {
				var bd = document.getElementsByTagName('body')[0],
					container = bd.appendChild(document.createElement('div'));
				oCfg.containerID = Dom.generateId(container);
			}

			if (! oCfg.attributes) {oCfg.attributes  = {};}
			if (! oCfg.attributes.flashVars) {oCfg.attributes.flashVars = {};}
			oCfg.attributes.flashVars.allowedDomain = document.location.hostname;
			oCfg.attributes.flashVars.useCompression = 'true';
			oCfg.attributes.version = 9.115;
			_driver = new Y.widget.SWF(oCfg.containerID, oCfg.swfURL, oCfg.attributes);

			// subscribe to save for info
			_driver.subscribe('save', function(o) {
				Y.log(o.message, 'info');
			});

			// subscribe to errors
			_driver.subscribe('quotaExceededError', function(o) {
				Y.log(o.message, 'error');
			});
			_driver.subscribe('inadequateDimensions', function(o) {
				Y.log(o.message, 'error');
			});
			_driver.subscribe('error', function(o) {
				Y.log(o.message, 'error');
			});
			_driver.subscribe('securityError', function(o) {
				Y.log(o.message, 'error');
			});
		}
	},

	/**
	 * The StorageEngineSWF class implements the SWF storage engine.
	 * @namespace YAHOO.util
	 * @class StorageEngineSWF
	 * @uses YAHOO.widget.SWF
	 * @constructor
	 * @extend YAHOO.util.Storage
	 * @param sLocation {String} Required. The storage location.
	 * @param oConf {Object} Required. A configuration object.
	 */
	StorageEngineSWF = function(sLocation, oConf) {
		var that = this;
		StorageEngineSWF.superclass.constructor.call(that, sLocation, StorageEngineSWF.ENGINE_NAME, oConf);
		
		_initDriver(that._cfg);
		
		var _onContentReady = function() {
			that._swf = _driver._swf;
			_driver.initialized = true;
			
			var isSessionStorage = StorageManager.LOCATION_SESSION === that._location,
				sessionKey = Util.Cookie.get('sessionKey' + StorageEngineSWF.ENGINE_NAME),
                i, key, isKeySessionStorage;

			for (i = _driver.callSWF("getLength", []) - 1; 0 <= i; i -= 1) {
				key = _driver.callSWF("getNameAt", [i]);
				isKeySessionStorage = isSessionStorage && (-1 < key.indexOf(StorageManager.LOCATION_SESSION));

				// this is session storage, but the session key is not set, so remove item
				if (isKeySessionStorage && ! sessionKey) {
					_driver.callSWF("removeItem", [key]);
				}
				else if (isSessionStorage === isKeySessionStorage) {
                    // the key matches the storage type, add to key collection
					that._addKey(key);
				}
			}

			// this is session storage, ensure that the session key is set
			if (isSessionStorage) {
				Util.Cookie.set('sessionKey' + StorageEngineSWF.ENGINE_NAME, true);
			}

			that.fireEvent(Util.Storage.CE_READY);
		};
		
		// evaluate immediately, SWF is already loaded
		if (_driver.initialized) {
            _onContentReady();
		}
		else {
            // evaluates when the SWF is loaded
			_driver.addListener("contentReady", _onContentReady);
		}
	};

	Lang.extend(StorageEngineSWF, Util.StorageEngineKeyed, {
		/**
		 * The underlying SWF of the engine, exposed so developers can modify the adapter behavior.
		 * @property _swf
		 * @type {Object}
		 * @protected
		 */
		_swf: null,

		/*
		 * Implementation to clear the values from the storage engine.
		 * @see YAHOO.util.Storage._clear
		 */
		_clear: function() {
			for (var i = this._keys.length - 1, sKey; 0 <= i; i -= 1) {
				sKey = this._keys[i];
				_driver.callSWF("removeItem", [sKey]);
			}
			// since keys are used to clear, we call the super function second
			StorageEngineSWF.superclass._clear.call(this);
		},

		/*
		 * Implementation to fetch an item from the storage engine.
		 * @see YAHOO.util.Storage._getItem
		 */
		_getItem: function(sKey) {
			var sLocationKey = _getKey(this, sKey);
			return _driver.callSWF("getValueOf", [sLocationKey]);
		},

		/*
		 * Implementation to fetch a key from the storage engine.
		 * @see YAHOO.util.Storage.key
		 */
		_key: function(index) {
			return StorageEngineSWF.superclass._key.call(this, index).replace(RX_STORAGE_PREFIX, '');
		},

		/*
		 * Implementation to remove an item from the storage engine.
		 * @see YAHOO.util.Storage._removeItem
		 */
		_removeItem: function(sKey) {
			Y.log("removing SWF key: " + sKey);
			var sLocationKey = _getKey(this, sKey);
			StorageEngineSWF.superclass._removeItem.call(this, sLocationKey);
			_driver.callSWF("removeItem", [sLocationKey]);
		},

		/*
		 * Implementation to remove an item from the storage engine.
		 * @see YAHOO.util.Storage._setItem
		 */
		_setItem: function(sKey, oData) {
			var sLocationKey = _getKey(this, sKey), swfNode;

			if (_driver.callSWF("setItem", [sLocationKey, oData])) {
				this._addKey(sLocationKey);
				return true;
			}
			else {
                /*
                    note:
                        right if the FLASH SLO size needs to be adjusted, then this request and all future requests fail
                        should we queue these up and poll for when there is enough space?
                 */
				swfNode = Dom.get(_driver._id);
				if (MINIMUM_WIDTH > Dom.getStyle(swfNode, 'width').replace(/\D+/g, '')) {Dom.setStyle(swfNode, 'width', MINIMUM_WIDTH + 'px');}
				if (MINIMUM_HEIGHT > Dom.getStyle(swfNode, 'height').replace(/\D+/g, '')) {Dom.setStyle(swfNode, 'height', MINIMUM_HEIGHT + 'px');}
				Y.log("attempting to show settings. are dimensions adequate? " + _driver.callSWF("hasAdequateDimensions"));
				return _driver.callSWF("displaySettings", []);
			}
		}
	});

	StorageEngineSWF.SWFURL = "swfstore.swf";
	StorageEngineSWF.ENGINE_NAME = 'swf';

    StorageEngineSWF.isAvailable = function() {
		return (6 <= Y.env.ua.flash && Y.widget.SWF);
	};

    StorageManager.register(StorageEngineSWF);
	Util.StorageEngineSWF = StorageEngineSWF;
}());
YAHOO.register("storage", YAHOO.util.Storage, {version: "2.9.0", build: "2800"});
