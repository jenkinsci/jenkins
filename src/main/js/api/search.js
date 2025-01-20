/**
 * Performs a search query using the provided search term.
 * @param {string} searchTerm - The term to search for.
 * @returns {Promise<Response>} - A promise resolving to the fetch response.
 * @throws {Error} - Throws an error if the search URL is missing or invalid.
 */
function search(searchTerm) {
  if (typeof searchTerm !== "string" || searchTerm.trim() === "") {
    throw new Error("Invalid search term. It must be a non-empty string.");
  }

  const buttonElement = document.getElementById("button-open-command-palette");
  const address = buttonElement?.dataset?.searchUrl;

  if (!address) {
    throw new Error("Search URL is missing. Ensure the data-search-url attribute is set.");
  }

  const queryUrl = `${address}?query=${encodeURIComponent(searchTerm)}`;
  
  return fetch(queryUrl)
    .then((response) => {
      if (!response.ok) {
        throw new Error(`Search failed with status: ${response.status}`);
      }
      return response;
    })
    .catch((error) => {
      console.error("Error performing search:", error);
      throw error;
    });
}

export default { search };

