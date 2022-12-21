// Group results by 'category' field into map
function groupResultsByCategory(array) {
  return array.reduce((hash, obj) => {
    if (obj.category === undefined) {return hash;}
    return Object.assign(hash, {
      [obj.category]: (hash[obj.category] || []).concat(obj),
    });
  }, {});
}

export default { groupResultsByCategory: groupResultsByCategory };
