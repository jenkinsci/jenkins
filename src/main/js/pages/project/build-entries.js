const ENTRY_ID_ATTRIBUTE = "page-entry-id";

/**
 * The HTML the server last rendered for each entry on the page. Entries are
 * compared against this rather than their live DOM, which may since have been
 * changed client-side.
 * @type {WeakMap<Element, string>}
 */
const serverHtml = new WeakMap();

/**
 * Updates the entries in `contents` to match `responseText`, keyed by page
 * entry ID. Entries whose server-rendered HTML hasn't changed are left in
 * place, untouched; changed entries are replaced, new ones inserted, and ones
 * no longer on the page removed.
 *
 * Expects `responseText` to be a single wrapper element whose children are the
 * entries, and copies the wrapper's attributes onto the existing one.
 * @param {HTMLElement} contents
 * @param {string} responseText
 * @return {Element[]} the entries that were inserted, which need behaviours applied
 */
export function updateEntries(contents, responseText) {
  const template = document.createElement("template");
  template.innerHTML = responseText;
  const incoming = template.content.firstElementChild;
  const incomingEntries = Array.from(incoming.children);
  incomingEntries.forEach((entry) => serverHtml.set(entry, entry.outerHTML));

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
