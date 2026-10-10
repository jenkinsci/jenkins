const ROW_SELECTOR = ".app-build-item--columns";
const COLUMN_CLASS = "app-build-item__column";
const HIDDEN_COLUMN_CLASS = `${COLUMN_CLASS}--hidden`;
const ACTIONS_SELECTOR = ".app-temporary-list__item__actions";
const MEASURING_CLASS = "app-build-columns--measuring";

/**
 * Widest each build's cells have been, by page entry ID. Rows are re-rendered
 * on every refresh and some decorators fill themselves in client-side after
 * that, so without this a column would briefly shrink (and the columns after
 * it fit) until they do.
 * @type {WeakMap<HTMLElement, Map<string, number[]>>}
 */
const rememberedWidths = new WeakMap();

/**
 * Sizes the columns of the build rows inside the given element so that each
 * column is as wide as its widest cell (capped by the stylesheet) and lines up
 * across rows. The first column is always shown; later columns are shown in
 * order for as long as they fit, and the rest are hidden.
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
    const rowWidths = cellsByRow[r].map((cell, i) =>
      Math.max(Math.ceil(cell.getBoundingClientRect().width), previous[i] ?? 0),
    );
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
    let hasContent = i === 0;
    cellsByRow.forEach((cells, r) => {
      const cell = cells[i];
      if (!cell) {
        return;
      }
      width = Math.max(width, widthsByRow[r][i]);
      hasContent ||=
        cell.childElementCount > 0 || cell.textContent.trim() !== "";
    });
    columns.push({ width, hasContent });
  }

  container.classList.remove(MEASURING_CLASS);

  // Every row is the same width, so fit the columns against the first one
  const actions = rows[0].querySelector(ACTIONS_SELECTOR);
  const available =
    rows[0].clientWidth - (actions ? actions.getBoundingClientRect().width : 0);

  let used = 0;
  let full = false;
  columns.forEach((column, i) => {
    // Columns with nothing in any row are dropped without taking up space
    column.visible = i === 0 || (!full && column.hasContent);
    if (i > 0 && column.visible && used + column.width > available) {
      column.visible = false;
      full = true;
    }
    if (column.visible) {
      used += column.width;
    }
  });

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
      // Remembered widths may be capped by the old viewport size, so measure afresh
      rememberedWidths.delete(container);
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
