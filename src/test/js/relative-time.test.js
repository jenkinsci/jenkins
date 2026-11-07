import { describe, expect, it } from "vitest";
import {
  formatMessage,
  formatTimeSpan,
  update,
} from "@/components/relative-time";

const EN = {
  locale: "en",
  second: "{0} sec",
  minute: "{0} min",
  hour: "{0} hr",
  day: "{0} {0,choice,0#days|1#day|1<days}",
  month: "{0} mo",
  year: "{0} yr",
};

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

describe("formatMessage", () => {
  it("picks choice sub-messages", () => {
    const pattern = "{0} {0,choice,0#дней|1#день|1<дня|5<дней}";
    expect(formatMessage(pattern, 0, "ru")).toBe("0 дней");
    expect(formatMessage(pattern, 1, "ru")).toBe("1 день");
    expect(formatMessage(pattern, 3, "ru")).toBe("3 дня");
    expect(formatMessage(pattern, 7, "ru")).toBe("7 дней");
  });

  it("formats decimals for the locale", () => {
    expect(formatMessage("{0} s", 1.2, "de")).toBe("1,2 s");
  });
});

describe("formatTimeSpan", () => {
  it.each([
    [50, "0 sec"],
    [5900, "5 sec"],
    [12 * SECOND, "12 sec"],
    [3 * MINUTE + 4 * SECOND, "3 min 4 sec"],
    [13 * MINUTE + 4 * SECOND, "13 min"],
    [2 * HOUR + 5 * MINUTE, "2 hr 5 min"],
    [DAY + 3 * HOUR, "1 day 3 hr"],
    [2 * DAY, "2 days 0 hr"],
    [400 * DAY, "1 yr 1 mo"],
  ])("formats %i ms as %s", (duration, expected) => {
    expect(formatTimeSpan(duration, EN)).toBe(expected);
  });
});

describe("update", () => {
  it("advances from the server-rendered elapsed time", () => {
    const element = document.createElement("span");
    element.dataset.relativeTimeElapsed = String(5 * SECOND);
    element.dataset.relativeTimeTemplate = "Started {0} ago";
    element.dataset.relativeTimePatterns = JSON.stringify(EN);

    update(element, Date.now() + 60 * SECOND);

    expect(element.textContent).toMatch(/^Started 1 min [56] sec ago$/);
  });

  it("counts down to times in the future", () => {
    const element = document.createElement("span");
    element.dataset.relativeTimeElapsed = String(-60 * SECOND);
    element.dataset.relativeTimeTemplate = "in {0}";
    element.dataset.relativeTimePatterns = JSON.stringify(EN);

    update(element, Date.now() + 15 * SECOND);

    expect(element.textContent).toMatch(/^in 4[45] sec$/);
  });
});
