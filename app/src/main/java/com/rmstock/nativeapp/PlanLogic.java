package com.rmstock.nativeapp;

import java.util.*;

/** Pure calculation helpers (no Android classes) so they can be unit-tested on a plain JVM. */
final class PlanLogic {
    private PlanLogic() {}

    static final int OK = 0, SOON = 1, LOW = 2, NEG = 3;

    static boolean validDate(String s) {
        if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        int y = Integer.parseInt(s.substring(0, 4)), m = Integer.parseInt(s.substring(5, 7)), d = Integer.parseInt(s.substring(8, 10));
        return m >= 1 && m <= 12 && d >= 1 && d <= daysIn(y, m);
    }

    static int daysIn(int y, int m) {
        if (m == 2) return ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) ? 29 : 28;
        return (m == 4 || m == 6 || m == 9 || m == 11) ? 30 : 31;
    }

    static String shiftDate(String ymd, int days) {
        Calendar c = new GregorianCalendar(Integer.parseInt(ymd.substring(0, 4)), Integer.parseInt(ymd.substring(5, 7)) - 1, Integer.parseInt(ymd.substring(8, 10)));
        c.add(Calendar.DAY_OF_MONTH, days);
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Whole days from a to b (negative when b is before a). */
    static int daysBetween(String a, String b) {
        Calendar x = new GregorianCalendar(Integer.parseInt(a.substring(0, 4)), Integer.parseInt(a.substring(5, 7)) - 1, Integer.parseInt(a.substring(8, 10)), 12, 0);
        Calendar y = new GregorianCalendar(Integer.parseInt(b.substring(0, 4)), Integer.parseInt(b.substring(5, 7)) - 1, Integer.parseInt(b.substring(8, 10)), 12, 0);
        return (int) Math.round((y.getTimeInMillis() - x.getTimeInMillis()) / 86400000.0);
    }

    /** Days the stock will last at the average daily use; -1 when there is no use to measure. */
    static double daysLeft(double closing, double avgPerDay) {
        if (avgPerDay <= 0) return -1;
        return Math.max(0, closing) / avgPerDay;
    }

    /** NEG: below zero, LOW: at or under the reorder level, SOON: less than 7 days left, otherwise OK. */
    static int status(double closing, double reorder, double avgPerDay) {
        if (closing < -0.0005) return NEG;
        if (reorder > 0 && closing <= reorder) return LOW;
        double dl = daysLeft(closing, avgPerDay);
        if (dl >= 0 && dl < 7) return SOON;
        return OK;
    }

    /** Total raw material needed. Recipe rows are {rmCode, productCode, qtyPerBatch}; batches is productCode to planned batches. */
    static Map<String, Double> need(Map<String, Double> batches, List<String[]> recipe) {
        Map<String, Double> out = new TreeMap<>();
        for (String[] r : recipe) {
            Double b = batches.get(r[1]);
            if (b == null || b <= 0) continue;
            double q = parse(r[2]);
            if (q == 0) continue;
            Double o = out.get(r[0]);
            out.put(r[0], (o == null ? 0 : o) + b * q);
        }
        return out;
    }

    /** Whole batches of one product the stock allows; -1 when the product has no recipe. */
    static double maxBatches(Map<String, Double> stock, List<String[]> productRecipe) {
        double best = Double.MAX_VALUE;
        boolean any = false;
        for (String[] r : productRecipe) {
            double q = parse(r[2]);
            if (q <= 0) continue;
            any = true;
            Double s = stock.get(r[0]);
            double have = s == null ? 0 : Math.max(0, s);
            best = Math.min(best, Math.floor(have / q + 1e-9));
        }
        return any ? best : -1;
    }

    static double parse(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }
}
