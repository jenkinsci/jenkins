const ENTRY_ID_ATTRIBUTE = "page-entry-id";
// An entry's cells are the children of this element, e.g. its columns
const CELLS_SELECTOR = ".app-build-item";

/**
 * The HTML the server last rendered for each entry, and each of their cells,
 * on the page. They're compared against this rather than their live DOM, which
 * may since have been changed client-side.
 * @type {WeakMap<Element, string>}
 */
const serverHtml = new WeakMap();

/**
 * Updates the entries in `contents` to match `responseText`, keyed by page
 * entry ID. Entries whose server-rendered HTML hasn't changed are left in
 * place, untouched; changed entries have just their changed cells replaced
 * (or the whole entry, if its cells no longer line up), new ones are inserted,
 * and ones no longer on the page removed.
 *
 * Expects `responseText` to be a single wrapper element whose children are the
 * entries, and copies the wrapper's attributes onto the existing one.
 * @param {HTMLElement} contents
 * @param {string} responseText
 * @return {Element[]} the entries and cells that were inserted, which need behaviours applied
 */
export function updateEntries(contents, responseText) {
  const template = document.createElement("template");
  template.innerHTML = responseText;
  const incoming = template.content.firstElementChild;
  const incomingEntries = Array.from(incoming.children);
  incomingEntries.forEach(remember);

  const wrapper = contents.firstElementChild;
  if (!wrapper) {
    contents.replaceChildren(incoming);
    return incomingEntries;
  }

  syncAttributes(wrapper, incoming);

  const existing = new Map();
  for (const entry of wrapper.children) {
    const id = entry.getAttribute(ENTRY_ID_ATTRIBUTE);
    if (id) {
      existing.set(id, entry);
    }
  }

  const inserted = [];
  let previous = null;
  for (const entry of incomingEntries) {
    const id = entry.getAttribute(ENTRY_ID_ATTRIBUTE);
    const current = id && existing.get(id);
    existing.delete(id);

    let node = entry;
    if (current && serverHtml.get(current) === serverHtml.get(entry)) {
      node = current;
    } else if (current && patchCells(current, entry, inserted)) {
      node = current;
    } else {
      inserted.push(entry);
      // Swap in place so the entries around it don't move
      current?.replaceWith(entry);
    }

    const expected = previous
      ? previous.nextElementSibling
      : wrapper.firstElementChild;
    if (node !== expected) {
      wrapper.insertBefore(node, expected);
    }
    previous = node;
  }

  // Anything left after the last entry is no longer on the page
  while (wrapper.lastElementChild && wrapper.lastElementChild !== previous) {
    wrapper.lastElementChild.remove();
  }

  return inserted;
}

/**
 * Replaces only the cells of `current` whose server-rendered HTML differs in
 * `incoming`, leaving the rest untouched.
 * @param {Element} current the entry on the page
 * @param {Element} incoming the newly rendered entry
 * @param {Element[]} inserted collects the cells that were swapped in
 * @return {boolean} false, changing nothing, if the entries' cells don't line up
 */
function patchCells(current, incoming, inserted) {
  const currentParent = current.querySelector(CELLS_SELECTOR);
  const incomingParent = incoming.querySelector(CELLS_SELECTOR);
  if (
    !currentParent ||
    !incomingParent ||
    current.childElementCount !== 1 ||
    incoming.childElementCount !== 1 ||
    currentParent.parentElement !== current ||
    incomingParent.parentElement !== incoming ||
    currentParent.childElementCount !== incomingParent.childElementCount
  ) {
    return false;
  }

  const currentCells = Array.from(currentParent.children);
  Array.from(incomingParent.children).forEach((cell, i) => {
    if (serverHtml.get(currentCells[i]) !== serverHtml.get(cell)) {
      currentCells[i].replaceWith(cell);
      inserted.push(cell);
    }
  });

  syncAttributes(current, incoming);
  syncAttributes(currentParent, incomingParent);
  serverHtml.set(current, serverHtml.get(incoming));
  return true;
}

/**
 * Records the server-rendered HTML of an entry and its cells.
 * @param {Element} entry
 */
function remember(entry) {
  serverHtml.set(entry, entry.outerHTML);
  const cells = entry.querySelector(CELLS_SELECTOR)?.children ?? [];
  for (const cell of cells) {
    serverHtml.set(cell, cell.outerHTML);
  }
}

/**
 * @param {Element} target
 * @param {Element} source
 */
function syncAttributes(target, source) {
  for (const { name } of Array.from(target.attributes)) {
    if (!source.hasAttribute(name)) {
      target.removeAttribute(name);
    }
  }
  for (const { name, value } of source.attributes) {
    if (target.getAttribute(name) !== value) {
      target.setAttribute(name, value);
    }
  }
}
