/**
 * Search Jenkins using Command Palette
 * @param {string} searchTerm - The term to search for
 * @returns {Promise} Resolves with search results or rejects with error
 */
function search(searchTerm) {
  const address = document.getElementById("button-open-command-palette")?.dataset?.searchUrl;
  if (!address) {
    return Promise.reject(new Error('Search endpoint not found'));
  }
  
  return fetch(`${address}?query=${encodeURIComponent(searchTerm)}`)
    .then(response => {
      if (!response.ok) throw new Error('Search failed with status ' + response.status);
      return response.json();
    })
    .catch(error => {
      console.error('Search error:', error);
      throw error;
    });
}

export default { search };
