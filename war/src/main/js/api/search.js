/**
 * @param {string} searchTerm
 */
async function search(searchTerm) {
  const address = document.getElementById("page-header").dataset.searchUrl;
  return fetch(`${address}?query=${encodeURIComponent(searchTerm)}`);
}

export default { search: search };
