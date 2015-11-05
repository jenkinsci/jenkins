// htmlunit doesn't have JSON, so emulate it
if (typeof JSON=="undefined") {
    var JSON = {
        parse : function (str) {
            return String.evalJSON(str);
        },
        stringify : function (obj) {
            return Object.toJSON(obj);
       }
    };
}