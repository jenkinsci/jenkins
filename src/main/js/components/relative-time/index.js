import behaviorShim from "@/util/behavior-shim";

const SELECTOR = "[data-relative-time-elapsed]";
const REFRESH_INTERVAL = 1000;

const ONE_SECOND_MS = 1000;
const ONE_MINUTE_MS = 60 * ONE_SECOND_MS;
const ONE_HOUR_MS = 60 * ONE_MINUTE_MS;
const ONE_DAY_MS = 24 * ONE_HOUR_MS;
const ONE_MONTH_MS = 30 * ONE_DAY_MS;
const ONE_YEAR_MS = 365 * ONE_DAY_MS;

/**
 * When each timestamp was rendered by the server, measured with the client's
 * clock so that clock skew between the two doesn't matter.
 * @type {WeakMap<Element, {elapsed: number, seenAt: number}>}
 */
const origins = new WeakMap();

/** @type {Map<string, object>} */
const parsedPatterns = new Map();

/**
 * Picks the sub-message of a java.text.ChoiceFormat pattern, e.g.
 * "0#days|1#day|1<days", for the given number.
 * @param {string} choices
 * @param {number} number
 * @return {string}
 */
function choose(choices, number) {
  let chosen = "";
  for (const [index, choice] of choices.split("|").entries()) {
    const match = choice.match(/^\s*(-?[\d.]+|∞|-∞)\s*([#<≤])(.*)$/s);
    if (!match) {
      continue;
    }
    const limit = Number(match[1].replace("∞", "Infinity"));
    const matches = match[2] === "<" ? number > limit : number >= limit;
    if (index === 0 || matches) {
      chosen = match[3];
    }
    if (!matches && index > 0) {
      break;
    }
  }
  return chosen;
}

/**
 * Formats a java.text.MessageFormat pattern with a single numeric argument,
 * supporting plain "{0}" and "{0,choice,...}" placeholders.
 * @param {string} pattern
 * @param {number} number
 * @param {string} locale
 * @return {string}
 */
export function formatMessage(pattern, number, locale) {
  const formattedNumber = new Intl.NumberFormat(locale).format(number);
  return pattern
    .replace(/\{0,\s*choice\s*,([^}]*)\}/g, (_, choices) =>
      choose(choices, number),
    )
    .replace(/\{0\}/g, formattedNumber)
    .replace(/''/g, "'");
}

/**
 * Mirrors hudson.Util#getTimeSpanString.
 * @param {number} duration in milliseconds
 * @param {object} patterns the localized hudson.Messages Util.* patterns, plus their locale
 * @return {string}
 */
export function formatTimeSpan(duration, patterns) {
  const unit = (name, value) =>
    formatMessage(patterns[name], value, patterns.locale);
  const pair = (big, bigName, small, smallName) => {
    const text = unit(bigName, big);
    if (big >= 10) {
      return text;
    }
    return `${text} ${unit(smallName, small)}`;
  };

  let remaining = Math.floor(Math.abs(duration));
  const years = Math.floor(remaining / ONE_YEAR_MS);
  remaining %= ONE_YEAR_MS;
  const months = Math.floor(remaining / ONE_MONTH_MS);
  remaining %= ONE_MONTH_MS;
  const days = Math.floor(remaining / ONE_DAY_MS);
  remaining %= ONE_DAY_MS;
  const hours = Math.floor(remaining / ONE_HOUR_MS);
  remaining %= ONE_HOUR_MS;
  const minutes = Math.floor(remaining / ONE_MINUTE_MS);
  remaining %= ONE_MINUTE_MS;
  const seconds = Math.floor(remaining / ONE_SECOND_MS);
  const millis = remaining % ONE_SECOND_MS;

  if (years > 0) {
    return pair(years, "year", months, "month");
  }
  if (months > 0) {
    return pair(months, "month", days, "day");
  }
  if (days > 0) {
    return pair(days, "day", hours, "hour");
  }
  if (hours > 0) {
    return pair(hours, "hour", minutes, "minute");
  }
  if (minutes > 0) {
    return pair(minutes, "minute", seconds, "second");
  }
  if (seconds >= 10) {
    return unit("second", seconds);
  }
  if (seconds >= 1) {
    return unit("second", seconds + Math.floor(millis / 100) / 10);
  }
  if (millis >= 100) {
    return unit("second", Math.floor(millis / 10) / 100);
  }
  return unit("millisecond", millis);
}

/**
 * @param {string} json
 * @return {object}
 */
function getPatterns(json) {
  if (!parsedPatterns.has(json)) {
    parsedPatterns.set(json, JSON.parse(json));
  }
  return parsedPatterns.get(json);
}

/**
 * @param {Element} element
 * @return {number}
 */
function track(element) {
  const seenAt = Date.now();
  origins.set(element, {
    elapsed: Number(element.dataset.relativeTimeElapsed),
    seenAt,
  });
  return seenAt;
}

/**
 * @param {Element} element
 * @param {number} now
 */
export function update(element, now = Date.now()) {
  if (!origins.has(element)) {
    track(element);
  }
  const { elapsed, seenAt } = origins.get(element);
  const patterns = getPatterns(element.dataset.relativeTimePatterns);
  const template = element.dataset.relativeTimeTemplate;
  const text = template.replace(
    "{0}",
    formatTimeSpan(elapsed + now - seenAt, patterns),
  );
  if (element.textContent !== text) {
    element.textContent = text;
  }
}

function updateAll() {
  if (document.hidden) {
    return;
  }
  const now = Date.now();
  document.querySelectorAll(SELECTOR).forEach((element) => {
    update(element, now);
  });
}

function init() {
  behaviorShim.specify(SELECTOR, "relative-time", 0, (element) => {
    track(element);
  });
  window.setInterval(updateAll, REFRESH_INTERVAL);
  document.addEventListener("visibilitychange", updateAll);
}

export default { init };
