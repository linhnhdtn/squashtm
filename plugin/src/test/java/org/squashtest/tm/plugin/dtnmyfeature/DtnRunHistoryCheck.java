package org.squashtest.tm.plugin.dtnmyfeature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Self-check for the branchy bits of the run-history report. Run with {@code ./plugin/build.sh
 * --check}; needs no database and no test framework.
 */
public final class DtnRunHistoryCheck {

    public static void main(String[] args) {
        trend();
        environmentFailures();
        System.out.println("DtnRunHistoryCheck OK");
    }

    private static void trend() {
        // cells are newest run first; null = the suite did not run this test case
        eq("not-run", DtnMyFeatureController.trend(new String[] {null, null}));
        eq("not-run", DtnMyFeatureController.trend(new String[] {"RUNNING", "READY", "CANCELLED"}));
        eq("stable", DtnMyFeatureController.trend(s("SUCCESS", "SUCCESS", "SUCCESS")));
        eq("regression", DtnMyFeatureController.trend(s("FAILURE", "SUCCESS", "SUCCESS")));
        eq("regression", DtnMyFeatureController.trend(s("ERROR", "SUCCESS", "SUCCESS")));
        eq("broken", DtnMyFeatureController.trend(s("FAILURE", "FAILURE", "SUCCESS")));
        eq("recovered", DtnMyFeatureController.trend(s("SUCCESS", "FAILURE", "FAILURE")));
        eq("flaky", DtnMyFeatureController.trend(s("SUCCESS", "FAILURE", "SUCCESS", "FAILURE")));

        // statuses without a verdict must not break a streak: this is still two bad runs in a row
        eq("broken", DtnMyFeatureController.trend(s("FAILURE", "CANCELLED", "FAILURE", "SUCCESS")));

        // only the 20 newest verdicts count: 24 stable runs after one old blip is not flaky
        String[] longTail = new String[80];
        java.util.Arrays.fill(longTail, "SUCCESS");
        for (int i = 40; i < 80; i += 2) {
            longTail[i] = "FAILURE"; // 20 flips, but all older than the trend window
        }
        eq("stable", DtnMyFeatureController.trend(longTail));
    }

    private static void environmentFailures() {
        // three test cases, three runs; every test case failed on run index 1 -> environment blip
        List<Map<String, Object>> rows =
                List.of(
                        row(s("SUCCESS", "FAILURE", "SUCCESS")),
                        row(s("SUCCESS", "FAILURE", "SUCCESS")),
                        row(s("SUCCESS", "ERROR", "SUCCESS")));
        eq(List.of(1), DtnMyFeatureController.environmentFailures(rows, 3));

        // a single test case failing alone is a bug, not an environment blip
        List<Map<String, Object>> mixed =
                List.of(row(s("SUCCESS", "FAILURE", "SUCCESS")), row(s("SUCCESS", "SUCCESS", "SUCCESS")));
        eq(List.of(), DtnMyFeatureController.environmentFailures(mixed, 3));

        // one lone test case must not make its own failure look environment-wide
        eq(List.of(), DtnMyFeatureController.environmentFailures(List.of(row(s("FAILURE"))), 1));
    }

    private static String[] s(String... cells) {
        return cells;
    }

    private static Map<String, Object> row(String[] cells) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cells", cells);
        return m;
    }

    private static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }

    private DtnRunHistoryCheck() {}
}
