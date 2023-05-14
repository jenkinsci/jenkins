// htmlunit doesn't have JSON, so emulate it
if (typeof JSON=="undefined") {
    var JSON = {
        parse : function (str) {
            return String.evalJSON(str);
        },
        stringify : function (obj) {
            // TODO simplify when Prototype.js is removed
            if (Object.toJSON) {
              // Prototype.js
              return Object.toJSON(obj);
            } else {
              // Standard
              return JSON.stringify(obj);
            }
       }
    };
}