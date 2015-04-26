/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.widgets.buildsearch.processors;

import hudson.model.Queue;
import hudson.model.Run;
import jenkins.widgets.buildsearch.BuildSearchParamProcessor;
import jenkins.widgets.buildsearch.BuildSearchParamProcessorFactory;
import jenkins.widgets.buildsearch.BuildSearchParams;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Search build history by {@link hudson.model.Queue.Item#getInQueueSince()}
 * or {@link hudson.model.Run#getTimeInMillis()} using one or both of the
 * "date-from:" and "date-to:" tokens. Also supports some shorthands such as "today"
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class DateProcessorFactory extends BuildSearchParamProcessorFactory {

    public static String LONG_DATE_FORMAT = "yyyy-MM-dd";
    public static String MEDIUM_DATE_FORMAT = "yy-MM-dd";
    public static String SHORT_DATE_FORMAT = "MM-dd";

    public static final long DAY = 1000 * 60 * 60 * 24;
    public static final long WEEK = DAY * 7;
    public static final long MONTH = WEEK * 4;

    /**
     * "date-from" search term.
     */
    public static final String DATE_FROM_ST = "date-from";
    /**
     * "date-to" search term.
     */
    public static final String DATE_TO_ST = "date-to";

    private static final String[] SEARCH_TERMS = new String[] {DATE_FROM_ST, DATE_TO_ST};

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getSearchTerms() {
        return SEARCH_TERMS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildSearchParamProcessor createProcessor(BuildSearchParams searchParams) {
        final DateFromTo dateFromTo = new DateFromTo(searchParams);

        if (dateFromTo.dateFrom == null && dateFromTo.dateTo == null) {
            return null;
        }

        return new BuildSearchParamProcessor<Long>() {

            @Override
            public boolean fitsSearchParams(Long timeInMillis) {
                if (dateFromTo.dateFrom != null && timeInMillis < dateFromTo.dateFrom) {
                    return false;
                }
                if (dateFromTo.dateTo != null && timeInMillis > dateFromTo.dateTo) {
                    return false;
                }
                return true;
            }

            @Override
            public boolean fitsSearchParams(Queue.Item item) {
                return fitsSearchParams(item.getInQueueSince());
            }
            @Override
            public boolean fitsSearchParams(Run run) {
                return fitsSearchParams(run.getTimeInMillis());
            }
        };
    }

    private class DateFromTo {

        private Long dateFrom;
        private Long dateTo;

        private DateFromTo(BuildSearchParams searchParams) {
            final List<BuildSearchParams.BuildSearchParam> dateFromParams = searchParams.getParams(DATE_FROM_ST);
            final List<BuildSearchParams.BuildSearchParam> dateToParams = searchParams.getParams(DATE_TO_ST);

            if (dateFromParams.isEmpty() && dateToParams.isEmpty()) {
                // none of the date search terms are specified
                return;
            }

            // Only supports spec of a single pair of "date-from:" and "date-to:" search params.
            if (!dateFromParams.isEmpty()) {
                dateFrom = toTimeInMillis(dateFromParams.get(0).get());
            } else {
                dateFrom = null;
            }

            if (!dateToParams.isEmpty()) {
                dateTo = toTimeInMillis(dateToParams.get(0).get());
            } else {
                dateTo = null;
            }

            if (dateFrom != null && dateTo != null && dateFrom > dateTo) {
                dateTo = null;
            }
        }
    }

    // make this available to test code too
    public static Long toTimeInMillis(String date) {
        date = date.toLowerCase();

        if (date.equals("today")) {
            return getToday();
        } else if (date.equals("yesterday")) {
            return getYesterday();
        } else if (date.endsWith("day") || date.endsWith("days")) {
            return getToday() - (extractMultiple(date) * DAY);
        } else if (date.endsWith("week") || date.endsWith("weeks")) {
            return getToday() - (extractMultiple(date) * WEEK);
        } else if (date.endsWith("month") || date.endsWith("months")) {
            return getToday() - (extractMultiple(date) * MONTH);
        }

        try {
            // SimpleDateFormat is not thread safe. What a PITA !!!
            date = normalize(date);
            if (date.length() == LONG_DATE_FORMAT.length()) {
                return new SimpleDateFormat(LONG_DATE_FORMAT).parse(date).getTime();
            } else if (date.length() == MEDIUM_DATE_FORMAT.length()) {
                return new SimpleDateFormat(MEDIUM_DATE_FORMAT).parse(date).getTime();
            } else {
                return null;
            }
        } catch (ParseException e) {
            return null;
        }
    }

    private static long extractMultiple(String date) {
        if (Character.isDigit(date.charAt(0))) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < date.length(); i++) {
                char c = date.charAt(i);
                if (!Character.isDigit(c)) {
                    break;
                }
                builder.append(c);
            }
            return new Long(builder.toString());
        }
        return 1;
    }

    // make this available to test code too
    public static String normalize(String date) {
        // In case the user forgot the format and used '/' instead of '-' as the delimiter.
        date = date.replace('/', '-');
        // In case there are spaces
        date = date.replace(" ", "");
        date = date.toLowerCase();

        if (date.length() == SHORT_DATE_FORMAT.length()) {
            date = getCurrentYearString() + "-" + date;
        }

        return date;
    }

    public static String getCurrentYearString() {
        return new SimpleDateFormat("yyyy").format(new Date());
    }

    public static long getToday() {
        long now = System.currentTimeMillis();
        long partOfToday = now % DAY;
        long startOfToday = now - partOfToday;

        return startOfToday;
    }

    public static long getTomorrow() {
        return getToday() + DAY;
    }

    public static long getYesterday() {
        return getToday() - DAY;
    }
}
