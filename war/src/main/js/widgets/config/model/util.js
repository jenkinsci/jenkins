export function toId(string) {
    string = string.trim();
    return 'config_' + string.replace(/[\W_]+/g, '_').toLowerCase();
}
