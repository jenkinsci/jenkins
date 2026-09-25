/**
 * Group results by 'group' field into a map
 */
export function groupResultsByCategory(array) {
  return array.reduce((hash, obj) => {
    if (obj.group === undefined) {
      return hash;
    }
    return Object.assign(hash, {
      [obj.group]: (hash[obj.group] || []).concat(obj),
    });
  }, {});
}
