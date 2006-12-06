/*
Copyright (c) 2006, Yahoo! Inc. All rights reserved.
Code licensed under the BSD License:
http://developer.yahoo.net/yui/license.txt
version: 0.12.0
*/

/**
 * The YAHOO object is the single global object used by YUI Library.  It
 * contains utility function for setting up namespaces, inheritance, and
 * logging.  YAHOO.util, YAHOO.widget, and YAHOO.example are namespaces
 * created automatically for and used by the library.
 * @module yahoo
 * @title  YAHOO Global
 */

/**
 * The YAHOO global namespace object
 * @class YAHOO
 * @static
 */
if (typeof YAHOO == "undefined") {
    var YAHOO = {};
}

/**
 * Returns the namespace specified and creates it if it doesn't exist
 * <pre>
 * YAHOO.namespace("property.package");
 * YAHOO.namespace("YAHOO.property.package");
 * </pre>
 * Either of the above would create YAHOO.property, then
 * YAHOO.property.package
 *
 * Be careful when naming packages. Reserved words may work in some browsers
 * and not others. For instance, the following will fail in Safari:
 * <pre>
 * YAHOO.namespace("really.long.nested.namespace");
 * </pre>
 * This fails because "long" is a future reserved word in ECMAScript
 *
 * @method namespace
 * @static
 * @param  {String*} arguments 1-n namespaces to create
 * @return {Object}  A reference to the last namespace object created
 */
YAHOO.namespace = function() {
    var a=arguments, o=null, i, j, d;
    for (i=0; i<a.length; ++i) {
        d=a[i].split(".");
        o=YAHOO;

        // YAHOO is implied, so it is ignored if it is included
        for (j=(d[0] == "YAHOO") ? 1 : 0; j<d.length; ++j) {
            o[d[j]]=o[d[j]] || {};
            o=o[d[j]];
        }
    }

    return o;
};

/**
 * Uses YAHOO.widget.Logger to output a log message, if the widget is available.
 *
 * @method log
 * @static
 * @param  {String}  msg  The message to log.
 * @param  {String}  cat  The log category for the message.  Default
 *                        categories are "info", "warn", "error", time".
 *                        Custom categories can be used as well. (opt)
 * @param  {String}  src  The source of the the message (opt)
 * @return {Boolean}      True if the log operation was successful.
 */
YAHOO.log = function(msg, cat, src) {
    var l=YAHOO.widget.Logger;
    if(l && l.log) {
        return l.log(msg, cat, src);
    } else {
        return false;
    }
};

/**
 * Utility to set up the prototype, constructor and superclass properties to
 * support an inheritance strategy that can chain constructors and methods.
 *
 * @method extend
 * @static
 * @param {Function} subc   the object to modify
 * @param {Function} superc the object to inherit
 * @param {String[]} overrides  additional properties/methods to add to the
 *                              subclass prototype.  These will override the
 *                              matching items obtained from the superclass
 *                              if present.
 */
YAHOO.extend = function(subc, superc, overrides) {
    var F = function() {};
    F.prototype=superc.prototype;
    subc.prototype=new F();
    subc.prototype.constructor=subc;
    subc.superclass=superc.prototype;
    if (superc.prototype.constructor == Object.prototype.constructor) {
        superc.prototype.constructor=superc;
    }

    if (overrides) {
        for (var i in overrides) {
            subc.prototype[i]=overrides[i];
        }
    }
};

/**
 * Applies all prototype properties in the supplier to the receiver if the
 * receiver does not have these properties yet.  Optionally, one or more
 * methods/properties can be specified (as additional parameters).  This
 * option will overwrite the property if receiver has it already.
 *
 * @method augment
 * @static
 * @param {Function} r  the object to receive the augmentation
 * @param {Function} s  the object that supplies the properties to augment
 * @param {String*}  arguments zero or more properties methods to augment the
 *                             receiver with.  If none specified, everything
 *                             in the supplier will be used unless it would
 *                             overwrite an existing property in the receiver
 */
YAHOO.augment = function(r, s) {
    var rp=r.prototype, sp=s.prototype, a=arguments, i, p;
    if (a[2]) {
        for (i=2; i<a.length; ++i) {
            rp[a[i]] = sp[a[i]];
        }
    } else {
        for (p in sp) {
            if (!rp[p]) {
                rp[p] = sp[p];
            }
        }
    }
};

YAHOO.namespace("util", "widget", "example");

