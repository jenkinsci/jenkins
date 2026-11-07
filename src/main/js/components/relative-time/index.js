import behaviorShim from "@/util/behavior-shim";

const SELECTOR = "[data-relative-time-elapsed]";

const ONE_SECOND_MS = 1000;
const ONE_MINUTE_MS = 60 * ONE_SECOND_MS;
const ONE_HOUR_MS = 60 * ONE_MINUTE_MS;
const ONE_DAY_MS = 24 * ONE_HOUR_MS;

// Largest first, as in hudson.Util#getTimeSpanString
const UNITS = [
  ["year", 365 * ONE_DAY_MS],
  ["month", 30 * ONE_DAY_MS],
  ["day", ONE_DAY_MS],
  ["hour", ONE_HOUR_MS],
  ["minute", ONE_MINUTE_MS],
  ["second", ONE_SECOND_MS],
];

/**
 * The client time each element's time span is measured from, and the
 * template it's shown in.
 * @type {Map<Element, {origin: number, template: string}>}
 */
const tracked = new Map();

let formatter;
let interval;

/**
 * @param {{limit: number, exclusive: boolean, text: string}[]} options
 * @param {number} number
 * @return {string}
 */
function choose(options, number) {
  let text = options[0]?.text ?? "";
  for (const option of options.slice(1)) {
    const reached = option.exclusive
      ? number > option.limit
      : number >= option.limit;
    if (!reached) {
      break;
    }
    text = option.text;
  }
  return text;
}

/**
 * Compiles a java.text.MessageFormat pattern with a single numeric argument,
 * supporting "{0}" and the "{0,choice,0#days|1#day|1<days}" form used by the
 * hudson.Messages Util.* patterns.
 * @param {string} pattern
 * @param {string} locale
 * @return {(number: number) => string}
 */
export function compileMessage(pattern, locale) {
  const numberFormat = new Intl.NumberFormat(locale);
  const pieces = pattern
    .replace(/''/g, "'")
    .split(/(\{0(?:,\s*choice\s*,[^}]*)?\})/)
    .map((part, index) => {
      if (index % 2 === 0) {
        return () => part;
      }
      const choices = part.match(/^\{0,\s*choice\s*,([^}]*)\}$/);
      if (!choices) {
        return (number) => numberFormat.format(number);
      }
      const options = choices[1]
        .split("|")
        .map((choice) => choice.match(/^(\d+)([#<])(.*)$/s))
        .filter(Boolean)
        .map(([, limit, operator, text]) => ({
          limit: Number(limit),
          exclusive: operator === "<",
          text,
        }));
      return (number) => choose(options, number);
    });
  return (number) => pieces.map((piece) => piece(number)).join("");
}

/**
 * Mirrors hudson.Functions#formatTimeSpan: hudson.Util#getTimeSpanString,
 * but to whole seconds so that it doesn't jitter while ticking.
 * @param {object} patterns the localized hudson.Messages Util.* patterns, plus their locale
 * @return {(duration: number) => string}
 */
export function createTimeSpanFormatter(patterns) {
  const formats = UNITS.map(([name]) =>
    compileMessage(patterns[name], patterns.locale),
  );
  const last = UNITS.length - 1;

  return (duration) => {
    let remaining = Math.abs(duration);
    const values = UNITS.map(([, size]) => {
      const value = Math.floor(remaining / size);
      remaining %= size;
      return value;
    });

    const index = values.findIndex((value) => value > 0);
    if (index === -1 || index === last) {
      return formats[last](values[last]);
    }
    const text = formats[index](values[index]);
    if (values[index] >= 10) {
      return text;
    }
    return `${text} ${formats[index + 1](values[index + 1])}`;
  };
}

/**
 * The page's time span formatter, from the patterns the layout puts on the body.
 * @return {((duration: number) => string) | undefined}
 */
function getFormatter() {
  const patterns = document.body.dataset.timeSpanPatterns;
  if (!formatter && patterns) {
    formatter = createTimeSpanFormatter(JSON.parse(patterns));
  }
  return formatter;
}

/**
 * @param {number} now
 */
export function updateAll(now = Date.now()) {
  if (document.hidden) {
    return;
  }
  const format = getFormatter();
  tracked.forEach(({ origin, template }, element) => {
    if (!element.isConnected) {
      tracked.delete(element);
      return;
    }
    const text = template.replace("{0}", format(now - origin));
    if (element.textContent !== text) {
      element.textContent = text;
    }
  });
  if (tracked.size === 0) {
    window.clearInterval(interval);
    interval = undefined;
  }
}

/**
 * @param {Element} element
 */
export function track(element) {
  if (!getFormatter()) {
    return;
  }
  tracked.set(element, {
    origin: Date.now() - Number(element.dataset.relativeTimeElapsed),
    template: element.dataset.relativeTimeTemplate,
  });
  interval ??= window.setInterval(updateAll, ONE_SECOND_MS);
}

function init() {
  behaviorShim.specify(SELECTOR, "relative-time", 0, track);
  document.addEventListener("visibilitychange", () => updateAll());
}

export default { init };
