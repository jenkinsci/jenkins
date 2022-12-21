/**
 * @param {string} searchTerm
 */
async function search(searchTerm) {
  const address = document
    .getElementById("page-header")
    .dataset.searchUrl.escapeHTML();
  return fetch(`${address}?query=${searchTerm}`);
}

export default { search: search };
