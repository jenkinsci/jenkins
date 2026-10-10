const ROW_SELECTOR = ".app-build-item--columns";
const COLUMN_CLASS = "app-build-item__column";
const HIDDEN_COLUMN_CLASS = `${COLUMN_CLASS}--hidden`;
const ACTIONS_SELECTOR = ".app-temporary-list__item__actions";
const MEASURING_CLASS = "app-build-columns--measuring";

/**
 * Widest each build's cells have been, by page entry ID. Rows are re-rendered
 * on every refresh and some decorators fill themselves in client-side after
 * that, so without this a column would briefly shrink (and the columns after
 * it fit) until they do. It also stops content that adapts to the width it's
 * given from settling on a smaller size after its column was hidden.
 * @type {WeakMap<HTMLElement, Map<string, number[]>>}
 */
const rememberedWidths = new WeakMap();

/**
 * Sizes the columns of the build rows inside the given element so that each
 * column is as wide as its widest cell (capped by the stylesheet) and lines up
 * across rows. The first column is always shown; later columns are shown in
 * order for as long as they fit, and the rest are hidden.
 *
 * A column whose cells have content but haven't rendered anything yet, e.g. a
 * decorator that renders client-side, or whose content can shrink (see below)
 * and hasn't been measured yet, is given its full max-width so its content can
 * render into that space. The next layout then shrinks it to what it used.
 *
 * Like flex items, a column's content can't shrink below its natural width by
 * default, so a column that doesn't fit is hidden. Content can opt in to
 * shrinking by giving its root element an explicit `min-width`, in which case
 * the column is shrunk down to that before it's hidden.
 * @param {HTMLElement} container
 */
export function layoutBuildColumns(container) {
  const rows = Array.from(container.querySelectorAll(ROW_SELECTOR));
  if (rows.length === 0) {
    return;
  }

  const cellsByRow = rows.map((row) =>
    Array.from(row.children).filter((e) => e.classList.contains(COLUMN_CLASS)),
  );
  const columnCount = Math.max(...cellsByRow.map((cells) => cells.length));

  // Reset any previous layout so each cell reports its natural width
  container.classList.add(MEASURING_CLASS);
  cellsByRow.flat().forEach((cell) => {
    cell.style.flexBasis = "";
    cell.classList.remove(HIDDEN_COLUMN_CLASS);
  });

  const previousWidths = rememberedWidths.get(container) ?? new Map();
  const widths = new Map();
  const widthsByRow = rows.map((row, r) => {
    const id = row.closest("[page-entry-id]")?.getAttribute("page-entry-id");
    const previous = (id && previousWidths.get(id)) || [];
    const rowWidths = cellsByRow[r].map((cell, i) => {
      // Content that can shrink adapts to the width it's given, so it can't
      // say how wide it wants to be until it's been offered the room
      const unmeasured =
        isPending(cell) ||
        (previous[i] === undefined && Number.isFinite(getMinWidth(cell)));
      const width = unmeasured
        ? 0
        : Math.ceil(cell.getBoundingClientRect().width);
      return Math.max(width, previous[i] ?? 0);
    });
    if (id) {
      widths.set(id, rowWidths);
    }
    return rowWidths;
  });
  // Only keep the rows still on the page
  rememberedWidths.set(container, widths);

  const columns = [];
  for (let i = 0; i < columnCount; i++) {
    let width = 0;
    let minWidth = 0;
    let hasContent = i === 0;
    let maxWidth = Infinity;
    cellsByRow.forEach((cells, r) => {
      const cell = cells[i];
      if (!cell) {
        return;
      }
      width = Math.max(width, widthsByRow[r][i]);
      minWidth = Math.max(minWidth, getMinWidth(cell));
      hasContent ||=
        cell.childElementCount > 0 || cell.textContent.trim() !== "";
      maxWidth = parseFloat(getComputedStyle(cell).maxWidth) || Infinity;
    });
    // Nothing has rendered in this column yet, so offer it all the room it may use
    if (hasContent && width === 0 && Number.isFinite(maxWidth)) {
      width = maxWidth;
    }
    columns.push({ width, minWidth: Math.min(minWidth, width), hasContent });
  }

  container.classList.remove(MEASURING_CLASS);

  // Every row is the same width, so fit the columns against the first one
  const actions = rows[0].querySelector(ACTIONS_SELECTOR);
  const available =
    rows[0].clientWidth - (actions ? actions.getBoundingClientRect().width : 0);

  // Show columns in order for as long as they fit at their smallest
  const [first, ...rest] = columns;
  first.visible = true;
  let used = first.width;
  let full = false;
  for (const column of rest) {
    // Columns with nothing in any row are dropped without taking up space
    column.visible =
      !full && column.hasContent && used + column.minWidth <= available;
    full ||= column.hasContent && !column.visible;
    if (column.visible) {
      used += column.minWidth;
    }
  }

  // Then shrink the columns that can, in proportion to how much they can, by
  // only as much as is needed for them all to fit
  const shown = rest.filter((column) => column.visible);
  const overflow =
    shown.reduce((sum, c) => sum + c.width, first.width) - available;
  const slack = shown.reduce((sum, c) => sum + c.width - c.minWidth, 0);
  if (overflow > 0 && slack > 0) {
    for (const column of shown) {
      const share = (overflow * (column.width - column.minWidth)) / slack;
      column.width = Math.max(
        column.minWidth,
        Math.floor(column.width - share),
      );
    }
  }

  cellsByRow.forEach((cells) => {
    cells.forEach((cell, i) => {
      if (columns[i].visible) {
        cell.style.flexBasis = `${columns[i].width}px`;
      } else {
        cell.classList.add(HIDDEN_COLUMN_CLASS);
      }
    });
  });
}

/**
 * Whether a cell has content that hasn't taken up any space yet.
 * @param {HTMLElement} cell
 * @return {boolean}
 */
function isPending(cell) {
  if (cell.childElementCount === 0) {
    return false;
  }
  return cell.getBoundingClientRect().width - getChromeWidth(cell) < 1;
}

/**
 * The narrowest a cell can be: the widest explicit `min-width` among its
 * content, or `Infinity` if any of it has the default `min-width: auto` and so
 * can't shrink.
 * @param {HTMLElement} cell
 * @return {number}
 */
function getMinWidth(cell) {
  // Bare text has no min-width to opt in with
  const hasText = Array.from(cell.childNodes).some(
    (node) => node.nodeType === Node.TEXT_NODE && node.textContent.trim(),
  );
  if (hasText) {
    return Infinity;
  }

  let minWidth = 0;
  for (const child of cell.children) {
    const style = getComputedStyle(child);
    if (style.display === "none") {
      continue;
    }
    if (!style.minWidth.endsWith("px")) {
      return Infinity;
    }
    minWidth = Math.max(minWidth, parseFloat(style.minWidth));
  }
  return Math.ceil(minWidth + getChromeWidth(cell));
}

/**
 * The horizontal padding and borders of an element.
 * @param {HTMLElement} element
 * @return {number}
 */
function getChromeWidth(element) {
  const style = getComputedStyle(element);
  return (
    parseFloat(style.paddingLeft) +
    parseFloat(style.paddingRight) +
    parseFloat(style.borderLeftWidth) +
    parseFloat(style.borderRightWidth)
  );
}

/**
 * Lays out the build columns whenever the container's width or contents change.
 * Contents can change after the rows are inserted, e.g. when a decorator
 * renders itself client-side, so the layout can't only run on insertion.
 * @param {HTMLElement} container
 */
export function observeBuildColumns(container) {
  // Both observers call back after the DOM changes but before the next paint,
  // so laying out here means rows are never painted with unaligned columns
  const layout = () => layoutBuildColumns(container);

  let lastWidth;
  new ResizeObserver(([entry]) => {
    const width = entry.contentRect.width;
    if (width !== lastWidth) {
      lastWidth = width;
      layout();
    }
  }).observe(container);

  // The layout itself only touches attributes, so this won't retrigger itself
  new MutationObserver(layout).observe(container, {
    childList: true,
    characterData: true,
    subtree: true,
  });
}
