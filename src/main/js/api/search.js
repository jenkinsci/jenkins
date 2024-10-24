/**
 * @param {string} searchTerm
 */
function search(searchTerm) {
  const address = document.getElementById("button-open-command-palette").dataset
    .searchUrl;
  return fetch(`${address}?query=${encodeURIComponent(searchTerm)}`);
}

export default { search: search };
