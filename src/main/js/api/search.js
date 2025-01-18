/**
 * @param {string} searchTerm
 */
function search(searchTerm) {
  const address = document.getElementById("body").dataset
    .searchUrl;
  return fetch(`${address}?query=${encodeURIComponent(searchTerm)}`);
}

export default { search: search };
