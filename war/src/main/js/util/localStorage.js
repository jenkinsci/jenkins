var windowHandle = require('window-handle');
var window = windowHandle.getWindow();
var localStorage = window.localStorage;

if (typeof window.localStorage === "undefined") {
    console.warn('HTML5 localStorage not supported by this browser.');
    // mock it...
    localStorage = {
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
    localStorage.setItem(name, value);
};

exports.getItem = function(name, defaultVal) {
    var value = localStorage.getItem(name);
    if (!value) {
        value = defaultVal;
    }
    return  value;
};

exports.removeItem = function(name) {
    return localStorage.removeItem(name);
};