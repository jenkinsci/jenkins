var windowHandle = require('window-handle');
var win = windowHandle.getWindow();
var storage = win.localStorage;

if (typeof storage === "undefined") {
    console.warn('HTML5 localStorage not supported by this browser.');
    // mock it...
    storage = {
        storage: {},
        setItem: function(name, value) {
            this.storage[name] = value;
        },
        getItem: function(name) {
            return this.storage[name];
        },
        removeItem: function(name) {
            delete this.storage[name];
        }
    };
}

exports.setItem = function(name, value) {
    storage.setItem(name, value);
};

exports.getItem = function(name, defaultVal) {
    var value = storage.getItem(name);
    if (!value) {
        value = defaultVal;
    }
    return  value;
};

exports.removeItem = function(name) {
    return storage.removeItem(name);
};