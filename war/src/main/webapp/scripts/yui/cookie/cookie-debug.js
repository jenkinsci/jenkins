/*
Copyright (c) 2011, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.com/yui/license.html
version: 2.9.0
*/
/**
 * Utilities for cookie management
 * @namespace YAHOO.util
 * @module cookie
 */
YAHOO.namespace("util");

/**
 * Cookie utility.
 * @class Cookie
 * @static
 */
YAHOO.util.Cookie = {
    
    //-------------------------------------------------------------------------
    // Private Methods
    //-------------------------------------------------------------------------
    
    /**
     * Creates a cookie string that can be assigned into document.cookie.
     * @param {String} name The name of the cookie.
     * @param {String} value The value of the cookie.
     * @param {Boolean} encodeValue True to encode the value, false to leave as-is.
     * @param {Object} options (Optional) Options for the cookie.
     * @return {String} The formatted cookie string.
     * @method _createCookieString
     * @private
     * @static
     */
    _createCookieString : function (name /*:String*/, value /*:Variant*/, encodeValue /*:Boolean*/, options /*:Object*/) /*:String*/ {
        
        //shortcut
        var lang = YAHOO.lang,
            text = encodeURIComponent(name) + "=" + (encodeValue ? encodeURIComponent(value) : value);
        
        
        if (lang.isObject(options)){
            //expiration date
            if (options.expires instanceof Date){
                text += "; expires=" + options.expires.toUTCString();
            }
            
            //path
            if (lang.isString(options.path) && options.path !== ""){
                text += "; path=" + options.path;
            }
            
            //domain
            if (lang.isString(options.domain) && options.domain !== ""){
                text += "; domain=" + options.domain;
            }
            
            //secure
            if (options.secure === true){
                text += "; secure";
            }
        }
        
        return text;
    },
    
    /**
     * Formats a cookie value for an object containing multiple values.
     * @param {Object} hash An object of key-value pairs to create a string for.
     * @return {String} A string suitable for use as a cookie value.
     * @method _createCookieHashString
     * @private
     * @static
     */
    _createCookieHashString : function (hash /*:Object*/) /*:String*/ {
        
        //shortcuts
        var lang = YAHOO.lang;
        
        if (!lang.isObject(hash)){
            throw new TypeError("Cookie._createCookieHashString(): Argument must be an object.");
        }
        
        var text /*:Array*/ = [];
        
        for (var key in hash){
            if (lang.hasOwnProperty(hash, key) && !lang.isFunction(hash[key]) && !lang.isUndefined(hash[key])){
                text.push(encodeURIComponent(key) + "=" + encodeURIComponent(String(hash[key])));
            }
        }
        
        return text.join("&");
    },
    
    /**
     * Parses a cookie hash string into an object.
     * @param {String} text The cookie hash string to parse. The string should already be URL-decoded.
     * @return {Object} An object containing entries for each cookie value.
     * @method _parseCookieHash
     * @private
     * @static
     */
    _parseCookieHash : function (text /*:String*/) /*:Object*/ {
        
        var hashParts /*:Array*/ = text.split("&"),
            hashPart /*:Array*/ = null,
            hash /*:Object*/ = {};
        
        if (text.length > 0){
            for (var i=0, len=hashParts.length; i < len; i++){
                hashPart = hashParts[i].split("=");
                hash[decodeURIComponent(hashPart[0])] = decodeURIComponent(hashPart[1]);
            }
        }
        
        return hash;
    },
    
    /**
     * Parses a cookie string into an object representing all accessible cookies.
     * @param {String} text The cookie string to parse.
     * @param {Boolean} decode (Optional) Indicates if the cookie values should be decoded or not. Default is true.
     * @return {Object} An object containing entries for each accessible cookie.
     * @method _parseCookieString
     * @private
     * @static
     */
    _parseCookieString : function (text /*:String*/, decode /*:Boolean*/) /*:Object*/ {
        
        var cookies /*:Object*/ = {};
        
        if (YAHOO.lang.isString(text) && text.length > 0) {
            
            var decodeValue = (decode === false ? function(s){return s;} : decodeURIComponent);
            
            //if (/[^=]+=[^=;]?(?:; [^=]+=[^=]?)?/.test(text)){
                var cookieParts /*:Array*/ = text.split(/;\s/g),
                    cookieName /*:String*/ = null,
                    cookieValue /*:String*/ = null,
                    cookieNameValue /*:Array*/ = null;
                
                for (var i=0, len=cookieParts.length; i < len; i++){
                    
                    //check for normally-formatted cookie (name-value)
                    cookieNameValue = cookieParts[i].match(/([^=]+)=/i);
                    if (cookieNameValue instanceof Array){
                        try {
                            cookieName = decodeURIComponent(cookieNameValue[1]);
                            cookieValue = decodeValue(cookieParts[i].substring(cookieNameValue[1].length+1));
                        } catch (ex){
                            //ignore the entire cookie - encoding is likely invalid
                        }
                    } else {
                        //means the cookie does not have an "=", so treat it as a boolean flag
                        cookieName = decodeURIComponent(cookieParts[i]);
                        cookieValue = "";
                    }
                    cookies[cookieName] = cookieValue;
                }
            //}
        }
        
        return cookies;
    },
    
    //-------------------------------------------------------------------------
    // Public Methods
    //-------------------------------------------------------------------------
    
    /**
     * Determines if the cookie with the given name exists. This is useful for
     * Boolean cookies (those that do not follow the name=value convention).
     * @param {String} name The name of the cookie to check.
     * @return {Boolean} True if the cookie exists, false if not.
     * @method exists
     * @static
     */
    exists: function(name) {

        if (!YAHOO.lang.isString(name) || name === ""){
            throw new TypeError("Cookie.exists(): Cookie name must be a non-empty string.");
        }

        var cookies /*:Object*/ = this._parseCookieString(document.cookie, true);
        
        return cookies.hasOwnProperty(name);
    },
    
    /**
     * Returns the cookie value for the given name.
     * @param {String} name The name of the cookie to retrieve.
     * @param {Object|Function} options (Optional) An object containing one or more
     *      cookie options: raw (true/false) and converter (a function).
     *      The converter function is run on the value before returning it. The
     *      function is not used if the cookie doesn't exist. The function can be
     *      passed instead of the options object for backwards compatibility.
     * @return {Variant} If no converter is specified, returns a string or null if
     *      the cookie doesn't exist. If the converter is specified, returns the value
     *      returned from the converter or null if the cookie doesn't exist.
     * @method get
     * @static
     */
    get : function (name /*:String*/, options /*:Variant*/) /*:Variant*/{
        
        var lang = YAHOO.lang,
            converter;
        
        if (lang.isFunction(options)) {
            converter = options;
            options = {};
        } else if (lang.isObject(options)) {
            converter = options.converter;
        } else {
            options = {};
        }
        
        var cookies /*:Object*/ = this._parseCookieString(document.cookie, !options.raw);
        
        if (!lang.isString(name) || name === ""){
            throw new TypeError("Cookie.get(): Cookie name must be a non-empty string.");
        }
        
        if (lang.isUndefined(cookies[name])) {
            return null;
        }
        
        if (!lang.isFunction(converter)){
            return cookies[name];
        } else {
            return converter(cookies[name]);
        }
    },
    
    /**
     * Returns the value of a subcookie.
     * @param {String} name The name of the cookie to retrieve.
     * @param {String} subName The name of the subcookie to retrieve.
     * @param {Function} converter (Optional) A function to run on the value before returning
     *      it. The function is not used if the cookie doesn't exist.
     * @return {Variant} If the cookie doesn't exist, null is returned. If the subcookie
     *      doesn't exist, null if also returned. If no converter is specified and the
     *      subcookie exists, a string is returned. If a converter is specified and the
     *      subcookie exists, the value returned from the converter is returned.
     * @method getSub
     * @static
     */
    getSub : function (name, subName, converter) {
        
        var lang = YAHOO.lang,
            hash = this.getSubs(name);
        
        if (hash !== null) {
            
            if (!lang.isString(subName) || subName === ""){
                throw new TypeError("Cookie.getSub(): Subcookie name must be a non-empty string.");
            }
            
            if (lang.isUndefined(hash[subName])){
                return null;
            }
            
            if (!lang.isFunction(converter)){
                return hash[subName];
            } else {
                return converter(hash[subName]);
            }
        } else {
            return null;
        }
    
    },
    
    /**
     * Returns an object containing name-value pairs stored in the cookie with the given name.
     * @param {String} name The name of the cookie to retrieve.
     * @return {Object} An object of name-value pairs if the cookie with the given name
     *      exists, null if it does not.
     * @method getSubs
     * @static
     */
    getSubs : function (name /*:String*/) /*:Object*/ {
    
        var isString = YAHOO.lang.isString;
        
        //check cookie name
        if (!isString(name) || name === ""){
            throw new TypeError("Cookie.getSubs(): Cookie name must be a non-empty string.");
        }
        
        var cookies = this._parseCookieString(document.cookie, false);
        if (isString(cookies[name])){
            return this._parseCookieHash(cookies[name]);
        }
        return null;
    },
    
    /**
     * Removes a cookie from the machine by setting its expiration date to
     * sometime in the past.
     * @param {String} name The name of the cookie to remove.
     * @param {Object} options (Optional) An object containing one or more
     *      cookie options: path (a string), domain (a string),
     *      and secure (true/false). The expires option will be overwritten
     *      by the method.
     * @return {String} The created cookie string.
     * @method remove
     * @static
     */
    remove : function (name /*:String*/, options /*:Object*/) /*:String*/ {
        
        //check cookie name
        if (!YAHOO.lang.isString(name) || name === ""){
            throw new TypeError("Cookie.remove(): Cookie name must be a non-empty string.");
        }
        
        //set options - clone options so the original isn't affected
        options = YAHOO.lang.merge(options || {}, {
            expires: new Date(0)
        });
        
        //set cookie
        return this.set(name, "", options);
    },
    
    /**
     * Removes a subcookie with a given name. Removing the last subcookie
     *      won't remove the entire cookie unless options.removeIfEmpty is true.
     * @param {String} name The name of the cookie in which the subcookie exists.
     * @param {String} subName The name of the subcookie to remove.
     * @param {Object} options (Optional) An object containing one or more
     *      cookie options: path (a string), domain (a string), expires (a Date object),
     *      removeIfEmpty (true/false), and secure (true/false). This must be the same
     *      settings as the original subcookie.
     * @return {String} The created cookie string.
     * @method removeSub
     * @static
     */
    removeSub : function(name /*:String*/, subName /*:String*/, options /*:Object*/) /*:String*/ {
        
        var lang = YAHOO.lang;
        
        options = options || {};
        
        //check cookie name
        if (!lang.isString(name) || name === ""){
            throw new TypeError("Cookie.removeSub(): Cookie name must be a non-empty string.");
        }
        
        //check subcookie name
        if (!lang.isString(subName) || subName === ""){
            throw new TypeError("Cookie.removeSub(): Subcookie name must be a non-empty string.");
        }
        
        //get all subcookies for this cookie
        var subs = this.getSubs(name);
        
        //delete the indicated subcookie
        if (lang.isObject(subs) && lang.hasOwnProperty(subs, subName)){
            delete subs[subName];

            if (!options.removeIfEmpty) {
                //reset the cookie

                return this.setSubs(name, subs, options);
            } else {
                //reset the cookie if there are subcookies left, else remove
                for (var key in subs){
                    if (lang.hasOwnProperty(subs, key) && !lang.isFunction(subs[key]) && !lang.isUndefined(subs[key])){
                        return this.setSubs(name, subs, options);
                    }
                }
                
                return this.remove(name, options);
            }
        } else {
            return "";
        }
        
    },
    
    /**
     * Sets a cookie with a given name and value.
     * @param {String} name The name of the cookie to set.
     * @param {Variant} value The value to set for the cookie.
     * @param {Object} options (Optional) An object containing one or more
     *      cookie options: path (a string), domain (a string), expires (a Date object),
     *      raw (true/false), and secure (true/false).
     * @return {String} The created cookie string.
     * @method set
     * @static
     */
    set : function (name /*:String*/, value /*:Variant*/, options /*:Object*/) /*:String*/ {
        
        var lang = YAHOO.lang;
        
        options = options || {};
        
        if (!lang.isString(name)){
            throw new TypeError("Cookie.set(): Cookie name must be a string.");
        }
        
        if (lang.isUndefined(value)){
            throw new TypeError("Cookie.set(): Value cannot be undefined.");
        }
        
        var text /*:String*/ = this._createCookieString(name, value, !options.raw, options);
        document.cookie = text;
        return text;
    },
    
    /**
     * Sets a sub cookie with a given name to a particular value.
     * @param {String} name The name of the cookie to set.
     * @param {String} subName The name of the subcookie to set.
     * @param {Variant} value The value to set.
     * @param {Object} options (Optional) An object containing one or more
     *      cookie options: path (a string), domain (a string), expires (a Date object),
     *      and secure (true/false).
     * @return {String} The created cookie string.
     * @method setSub
     * @static
     */
    setSub : function (name /*:String*/, subName /*:String*/, value /*:Variant*/, options /*:Object*/) /*:String*/ {
        
        var lang = YAHOO.lang;
        
        if (!lang.isString(name) || name === ""){
            throw new TypeError("Cookie.setSub(): Cookie name must be a non-empty string.");
        }
        
        if (!lang.isString(subName) || subName === ""){
            throw new TypeError("Cookie.setSub(): Subcookie name must be a non-empty string.");
        }
        
        if (lang.isUndefined(value)){
            throw new TypeError("Cookie.setSub(): Subcookie value cannot be undefined.");
        }
        
        var hash /*:Object*/ = this.getSubs(name);
        
        if (!lang.isObject(hash)){
            hash = {};
        }
        
        hash[subName] = value;
        
        return this.setSubs(name, hash, options);
        
    },
    
    /**
     * Sets a cookie with a given name to contain a hash of name-value pairs.
     * @param {String} name The name of the cookie to set.
     * @param {Object} value An object containing name-value pairs.
     * @param {Object} options (Optional) An object containing one or more
     *      cookie options: path (a string), domain (a string), expires (a Date object),
     *      and secure (true/false).
     * @return {String} The created cookie string.
     * @method setSubs
     * @static
     */
    setSubs : function (name /*:String*/, value /*:Object*/, options /*:Object*/) /*:String*/ {
        
        var lang = YAHOO.lang;
        
        if (!lang.isString(name)){
            throw new TypeError("Cookie.setSubs(): Cookie name must be a string.");
        }
        
        if (!lang.isObject(value)){
            throw new TypeError("Cookie.setSubs(): Cookie value must be an object.");
        }
        
        var text /*:String*/ = this._createCookieString(name, this._createCookieHashString(value), false, options);
        document.cookie = text;
        return text;
    }

};

YAHOO.register("cookie", YAHOO.util.Cookie, {version: "2.9.0", build: "2800"});
