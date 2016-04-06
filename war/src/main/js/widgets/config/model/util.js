
exports.toId = function(string) {
    string = string.trim();
    return 'config_' + string.replace(/[\W_]+/g, '_').toLowerCase();
};