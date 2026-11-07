import { afterEach, beforeAll, describe, expect, it } from "vitest";
import {
  compileMessage,
  createTimeSpanFormatter,
  track,
  updateAll,
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

describe("compileMessage", () => {
  it("picks choice sub-messages", () => {
    const format = compileMessage(
      "{0} {0,choice,0#дней|1#день|1<дня|5<дней}",
      "ru",
    );
    expect(format(0)).toBe("0 дней");
    expect(format(1)).toBe("1 день");
    expect(format(3)).toBe("3 дня");
    expect(format(7)).toBe("7 дней");
  });

  it("unescapes quotes", () => {
    expect(compileMessage("{0} o''clock", "en")(3)).toBe("3 o'clock");
  });
});

describe("createTimeSpanFormatter", () => {
  const format = createTimeSpanFormatter(EN);

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
    [-90 * SECOND, "1 min 30 sec"],
  ])("formats %i ms as %s", (duration, expected) => {
    expect(format(duration)).toBe(expected);
  });
});

describe("track", () => {
  beforeAll(() => {
    document.body.dataset.timeSpanPatterns = JSON.stringify(EN);
  });

  afterEach(() => {
    document.body.replaceChildren();
  });

  function element(elapsed, template) {
    const span = document.createElement("span");
    span.dataset.relativeTimeElapsed = String(elapsed);
    span.dataset.relativeTimeTemplate = template;
    document.body.append(span);
    track(span);
    return span;
  }

  it("advances from the server-rendered elapsed time", () => {
    const span = element(5 * SECOND, "Started {0} ago");

    updateAll(Date.now() + 60 * SECOND);

    expect(span.textContent).toMatch(/^Started 1 min [56] sec ago$/);
  });

  it("counts down to times in the future", () => {
    const span = element(-60 * SECOND, "in {0}");

    updateAll(Date.now() + 15 * SECOND);

    expect(span.textContent).toMatch(/^in 4[45] sec$/);
  });
});
