/**
 * @param {string} searchTerm
 * @param {string | undefined} scopeUrl
 */
function search(searchTerm, scopeUrl) {
  if (scopeUrl) {
    return fetch(
      `${scopeUrl}search/suggest?query=${encodeURIComponent(searchTerm)}&scoped=true`,
    );
  }

  const address = document.querySelector("body").dataset.searchUrl;
  return fetch(`${address}?query=${encodeURIComponent(searchTerm)}`);
}

export default { search: search };
