package org.squashtest.tm.plugin.dtnmyfeature;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.squashtest.tm.service.project.ProjectFinder;

/**
 * REST endpoints of the plugin.
 *
 * <p>WARNING: Squash TM leaves /plugin/** outside its authentication filter (plugin SPAs and
 * webhooks are served from there), so every method here must guard itself. We require an
 * authenticated Squash user via @PreAuthorize.
 */
@RestController
@RequestMapping("/plugin/dtn-myfeature/api")
@PreAuthorize("hasRole('ROLE_TM_USER') or hasRole('ROLE_ADMIN')")
public class DtnMyFeatureController {

    private static final int MAX_SUGGESTIONS = 15;

    /** Default width of the run-history sparkline. 302 is the worst case seen on the live data. */
    private static final int DEFAULT_RUNS = 30;

    private static final int MAX_RUNS = 300;

    /**
     * How many recent verdicts {@link #trend} looks at.
     *
     * <p>Bounded on purpose. Counting flips over the whole history made the label meaningless -- on
     * the live "Smoke test daily" iteration (302 runs) 19 of 21 test cases came out "flaky", because
     * over 300 runs nearly anything flips three times. Bounding it also keeps the label stable when
     * the user widens the sparkline.
     */
    private static final int TREND_WINDOW = 20;

    @PersistenceContext private EntityManager em;

    private final ProjectFinder projectFinder;

    DtnMyFeatureController(ProjectFinder projectFinder) {
        this.projectFinder = projectFinder;
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plugin", "dtn-myfeature");
        out.put("time", LocalDateTime.now().toString());
        out.put("projects", count("PROJECT"));
        out.put("testCases", count("TEST_CASE"));
        out.put("executions", count("EXECUTION"));
        out.put("requirements", count("REQUIREMENT"));
        return out;
    }

    @GetMapping("/project/{projectId}/test-cases")
    @Transactional(readOnly = true)
    public Map<String, Object> testCasesOfProject(@PathVariable long projectId) {
        Number n =
                (Number)
                        em.createNativeQuery(
                                        "select count(*) from TEST_CASE_LIBRARY_NODE where PROJECT_ID = :pid")
                                .setParameter("pid", projectId)
                                .getSingleResult();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        out.put("testCaseLibraryNodes", n.longValue());
        return out;
    }

    /**
     * Step suggestions for the BDD test case editor.
     *
     * <p>Squash ships this feature already (POST /backend/keyword-test-cases/autocomplete) but its
     * controller takes an Optional&lt;ActionWordService&gt; whose only implementation lives in the
     * proprietary premium plugin; without it the endpoint throws AccessDeniedException and the front
     * end falls back to a plain text input. The ACTION_WORD tables are core schema and already
     * populated by every step typed, so we read them directly.
     */
    @GetMapping("/test-case/{testCaseId}/action-words")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> actionWords(
            @PathVariable long testCaseId, @RequestParam(name = "q", defaultValue = "") String q) {
        // Narrowing on ACTION_WORD.token first matters: without it every keystroke pulls back every
        // fragment of every action word in the project and filters them in Java -- fine on a demo
        // project, thousands of rows on a real one. The token embeds the literal fragment text
        // ("T-Footer should be visible-") and is uniquely indexed on (token, project_id).
        // ponytail: a needle straddling a text/parameter boundary ("lbeo <par") is not in the token
        //           and so is not suggested; assemble() still does the precise match on the result.
        //
        // Squash runs on PostgreSQL, MariaDB, MySQL and SQL Server, and string concatenation has no
        // spelling common to all four: string_agg is PostgreSQL only, group_concat MySQL only, and
        // '<' || name || '>' silently evaluates as a boolean OR on MariaDB (yielding "0", no error).
        // So the query stays plain SQL-92 and the fragments are stitched together in Java below.
        List<Object[]> rows =
                em.createNativeQuery(
                                """
                                select distinct kts.keyword, f.action_word_id, f.fragment_order,
                                       t.text, p.name
                                from KEYWORD_TEST_STEP kts
                                join ACTION_WORD aw on aw.action_word_id = kts.action_word_id
                                 and aw.project_id =
                                     (select project_id from TEST_CASE_LIBRARY_NODE where tcln_id = :tcid)
                                join ACTION_WORD_FRAGMENT f on f.action_word_id = aw.action_word_id
                                left join ACTION_WORD_TEXT t
                                       on t.action_word_fragment_id = f.action_word_fragment_id
                                left join ACTION_WORD_PARAMETER p
                                       on p.action_word_fragment_id = f.action_word_fragment_id
                                where lower(aw.token) like :q
                                order by 1, 2, 3
                                """)
                        .setParameter("tcid", testCaseId)
                        .setParameter("q", "%" + likeLiteral(q.toLowerCase(Locale.ROOT)) + "%")
                        .getResultList();

        return assemble(rows, q);
    }

    /** Escapes the LIKE metacharacters so a query such as "50%" is matched literally. */
    private static String likeLiteral(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Folds the fragment rows -- (keyword, action word id, fragment order, text, parameter name),
     * already ordered by those first three -- into the suggestion list the front end expects.
     *
     * <p>Package private so the self-check in src/test can exercise it without a database.
     */
    static List<Map<String, String>> assemble(List<Object[]> rows, String q) {
        Map<List<Object>, StringBuilder> actions = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String fragment = r[3] != null ? (String) r[3] : "<" + r[4] + ">";
            actions.computeIfAbsent(List.of(r[0], r[1]), k -> new StringBuilder()).append(fragment);
        }

        String needle = q.toLowerCase(Locale.ROOT);
        List<Map<String, String>> found = new ArrayList<>();
        actions.forEach(
                (key, action) -> {
                    if (action.toString().toLowerCase(Locale.ROOT).contains(needle)) {
                        found.add(Map.of("keyword", (String) key.get(0), "action", action.toString()));
                    }
                });
        found.sort(
                Comparator.comparing((Map<String, String> m) -> m.get("action"))
                        .thenComparing(m -> m.get("keyword")));
        return found.size() > MAX_SUGGESTIONS ? found.subList(0, MAX_SUGGESTIONS) : found;
    }

    // ---------------------------------------------------------------- iteration run history

    /**
     * Iterations the caller may read, heaviest first.
     *
     * <p>Sorted by number of automated suites because that is what makes an iteration worth opening:
     * on the live data 379 iterations exist but only ~20 are re-run often enough to have a history.
     *
     * <p>Each entry also carries the campaign folder path, outermost folder first, so the page can
     * narrow 379 entries down by project and folder instead of scrolling one flat dropdown.
     */
    @GetMapping("/iterations")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> iterations() {
        List<Long> readable = readableProjectIds();
        if (readable.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows =
                em.createNativeQuery(
                                """
                                select it.ITERATION_ID, p.NAME, cln.NAME, it.NAME, it.REFERENCE,
                                       (select count(*) from AUTOMATED_SUITE a
                                         where a.ITERATION_ID = it.ITERATION_ID),
                                       (select count(*) from TEST_PLAN_ITEM tpi
                                         where tpi.TEST_PLAN_ID = it.TEST_PLAN_ID and tpi.DELETED = 0),
                                       (select max(a.CREATED_ON) from AUTOMATED_SUITE a
                                         where a.ITERATION_ID = it.ITERATION_ID)
                                from ITERATION it
                                join CAMPAIGN_ITERATION ci     on ci.ITERATION_ID = it.ITERATION_ID
                                join CAMPAIGN_LIBRARY_NODE cln on cln.CLN_ID = ci.CAMPAIGN_ID
                                join PROJECT p                 on p.PROJECT_ID = cln.PROJECT_ID
                                where p.PROJECT_ID in (:pids)
                                order by 6 desc, 8 desc
                                """)
                        .setParameter("pids", readable)
                        .getResultList();

        Map<Long, List<String>> folders = folderPaths(readable);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            long id = num(r[0]);
            m.put("iterationId", id);
            m.put("project", r[1]);
            m.put("campaign", r[2]);
            m.put("iteration", r[3]);
            m.put("reference", r[4]);
            m.put("suites", num(r[5]));
            m.put("items", num(r[6]));
            m.put("lastRunOn", r[7] == null ? null : r[7].toString());
            m.put("folders", folders.getOrDefault(id, List.of()));
            out.add(m);
        }
        return out;
    }

    /**
     * Campaign folder path of every iteration of the given projects, outermost folder first.
     *
     * <p>CLN_RELATIONSHIP_CLOSURE lists each node's ancestors with their distance, the node itself
     * included at depth 0; joining CAMPAIGN_FOLDER drops that self row, since a campaign is not a
     * folder. Ordering by depth descending therefore walks the path from the library down, and the
     * rows are folded in Java -- no dialect has a string aggregate the others understand.
     *
     * <p>A campaign sitting directly in the library has no folder at all and simply gets no entry.
     */
    @SuppressWarnings("unchecked")
    private Map<Long, List<String>> folderPaths(List<Long> readableProjectIds) {
        List<Object[]> rows =
                em.createNativeQuery(
                                """
                                select ci.ITERATION_ID, anc.NAME
                                from CAMPAIGN_ITERATION ci
                                join CLN_RELATIONSHIP_CLOSURE cl on cl.DESCENDANT_ID = ci.CAMPAIGN_ID
                                join CAMPAIGN_LIBRARY_NODE anc   on anc.CLN_ID = cl.ANCESTOR_ID
                                join CAMPAIGN_FOLDER cf          on cf.CLN_ID = anc.CLN_ID
                                where anc.PROJECT_ID in (:pids)
                                order by ci.ITERATION_ID, cl.DEPTH desc
                                """)
                        .setParameter("pids", readableProjectIds)
                        .getResultList();

        Map<Long, List<String>> out = new LinkedHashMap<>();
        for (Object[] r : rows) {
            out.computeIfAbsent(num(r[0]), k -> new ArrayList<>()).add((String) r[1]);
        }
        return out;
    }

    /**
     * Run history of one iteration: one row per test plan item, one cell per automated suite.
     *
     * <p>An iteration here is a long-lived scope re-run daily by CRON as an automated suite (live
     * data: 10k suites over 379 iterations, 99.9% of executions automated). So the interesting axis
     * is the suite, not the iteration -- and {@code TEST_PLAN_ITEM.EXECUTION_STATUS}, which Squash
     * keeps denormalized, only ever shows the last of those runs.
     *
     * <p>Totals span every run; the cells span the last {@code runs} suites only.
     */
    @GetMapping("/iteration/{iterationId}/run-history")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> runHistory(
            @PathVariable long iterationId,
            @RequestParam(name = "runs", defaultValue = "" + DEFAULT_RUNS) int runs) {

        Map<String, Object> meta = requireReadableIteration(iterationId);
        int window = Math.max(1, Math.min(runs, MAX_RUNS));

        // 1. totals over every run of the iteration
        List<Object[]> totals =
                em.createNativeQuery(
                                """
                                select tpi.TEST_PLAN_ITEM_ID, tpi.TCLN_ID, tc.REFERENCE,
                                       coalesce(tcln.NAME, tpi.LABEL), ds.NAME,
                                       count(*),
                                       sum(case when e.EXECUTION_STATUS = 'SUCCESS' then 1 else 0 end),
                                       sum(case when e.EXECUTION_STATUS in ('FAILURE','ERROR')
                                                then 1 else 0 end)
                                from AUTOMATED_SUITE a
                                join AUTOMATED_EXECUTION_EXTENDER aee on aee.SUITE_ID = a.SUITE_ID
                                join EXECUTION e        on e.EXECUTION_ID = aee.MASTER_EXECUTION_ID
                                join TEST_PLAN_ITEM tpi on tpi.TEST_PLAN_ITEM_ID = e.TEST_PLAN_ITEM_ID
                                left join TEST_CASE tc                on tc.TCLN_ID = tpi.TCLN_ID
                                left join TEST_CASE_LIBRARY_NODE tcln on tcln.TCLN_ID = tpi.TCLN_ID
                                left join DATASET ds                  on ds.DATASET_ID = tpi.DATASET_ID
                                where a.ITERATION_ID = :it
                                group by tpi.TEST_PLAN_ITEM_ID, tpi.TCLN_ID, tc.REFERENCE,
                                         tcln.NAME, tpi.LABEL, ds.NAME
                                """)
                        .setParameter("it", iterationId)
                        .getResultList();

        // 2. the last `window` suites, newest first -- these are the columns
        Query suiteQuery =
                em.createNativeQuery(
                                """
                                select a.SUITE_ID, a.CREATED_ON, a.EXECUTION_STATUS
                                from AUTOMATED_SUITE a
                                where a.ITERATION_ID = :it
                                order by a.CREATED_ON desc, a.SUITE_ID desc
                                """)
                        .setParameter("it", iterationId);
        suiteQuery.setMaxResults(window);
        List<Object[]> suiteRows = suiteQuery.getResultList();

        List<Map<String, Object>> suites = new ArrayList<>(suiteRows.size());
        List<Long> suiteIds = new ArrayList<>(suiteRows.size());
        Map<Long, Integer> columnOf = new LinkedHashMap<>();
        for (Object[] s : suiteRows) {
            long id = num(s[0]);
            columnOf.put(id, suiteIds.size());
            suiteIds.add(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("suiteId", id);
            m.put("createdOn", s[1] == null ? null : s[1].toString());
            m.put("status", s[2]);
            suites.add(m);
        }

        // 3. one execution per (suite, test plan item) -> the cells
        //    Assembled in Java on purpose: group_concat would be shorter but it is MariaDB-only and
        //    truncates at 1024 bytes by default, which a 302-run history silently exceeds.
        Map<Long, String[]> cellsOf = new LinkedHashMap<>();
        Map<Long, long[]> execIdOf = new LinkedHashMap<>();
        if (!suiteIds.isEmpty()) {
            List<Object[]> cellRows =
                    em.createNativeQuery(
                                    """
                                    select aee.SUITE_ID, e.TEST_PLAN_ITEM_ID, e.EXECUTION_STATUS,
                                           e.EXECUTION_ID
                                    from AUTOMATED_EXECUTION_EXTENDER aee
                                    join EXECUTION e on e.EXECUTION_ID = aee.MASTER_EXECUTION_ID
                                    where aee.SUITE_ID in (:ids) and e.TEST_PLAN_ITEM_ID is not null
                                    """)
                            .setParameter("ids", suiteIds)
                            .getResultList();
            for (Object[] c : cellRows) {
                Integer col = columnOf.get(num(c[0]));
                if (col == null) {
                    continue;
                }
                long itemId = num(c[1]);
                cellsOf.computeIfAbsent(itemId, k -> new String[suiteIds.size()])[col] =
                        (String) c[2];
                execIdOf.computeIfAbsent(itemId, k -> new long[suiteIds.size()])[col] = num(c[3]);
            }
        }

        List<Map<String, Object>> out = new ArrayList<>(totals.size());
        for (Object[] r : totals) {
            long itemId = num(r[0]);
            String[] cells = cellsOf.getOrDefault(itemId, new String[suiteIds.size()]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", itemId);
            m.put("testCaseId", r[1] == null ? null : num(r[1]));
            m.put("reference", r[2]);
            m.put("testCase", r[3]);
            m.put("dataset", r[4]);
            m.put("runs", num(r[5]));
            m.put("ok", num(r[6]));
            m.put("bad", num(r[7]));
            m.put("cells", cells);
            m.put("executionIds", execIdOf.get(itemId));
            m.put("trend", trend(cells));
            out.add(m);
        }
        out.sort(
                Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("bad"))
                        .reversed()
                        .thenComparing(m -> String.valueOf(m.get("testCase"))));

        Map<String, Object> res = new LinkedHashMap<>(meta);
        res.put("window", window);
        res.put("suites", suites);
        res.put("rows", out);
        res.put("environmentFailures", environmentFailures(out, suiteIds.size()));
        return res;
    }

    /**
     * Column indexes where every test case that ran produced a verdict and all of them were bad.
     *
     * <p>That pattern means the environment broke, not that N test cases regressed at once -- the
     * live "Health check" iteration shows all six of its test cases failing on the same single run.
     */
    static List<Integer> environmentFailures(List<Map<String, Object>> rows, int width) {
        List<Integer> out = new ArrayList<>();
        for (int col = 0; col < width; col++) {
            int verdicts = 0;
            int bad = 0;
            for (Map<String, Object> row : rows) {
                String status = ((String[]) row.get("cells"))[col];
                Boolean ok = verdict(status);
                if (ok != null) {
                    verdicts++;
                    if (!ok) {
                        bad++;
                    }
                }
            }
            if (verdicts > 1 && verdicts == bad) {
                out.add(col);
            }
        }
        return out;
    }

    /**
     * SUCCESS -> true, FAILURE/ERROR -> false, anything else (RUNNING, READY, CANCELLED, no run at
     * all) -> null, i.e. carries no verdict and must not break a streak.
     */
    static Boolean verdict(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "SUCCESS" -> Boolean.TRUE;
            case "FAILURE", "ERROR" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * Labels a run history, newest run first.
     *
     * <p>Only the last {@value #TREND_WINDOW} verdicts count, so the label describes recent
     * behaviour and does not drift when the caller widens the sparkline.
     *
     * <p>ponytail: threshold heuristic, not statistics -- a regression window that recovered inside
     * the last 20 runs still reads as "flaky". The sparkline next to the label shows the truth;
     * upgrade to a flip rate per calendar day if the label starts misleading people.
     */
    static String trend(String[] cellsNewestFirst) {
        List<Boolean> v = new ArrayList<>();
        for (String cell : cellsNewestFirst) {
            Boolean ok = verdict(cell);
            if (ok != null) {
                v.add(ok);
                if (v.size() == TREND_WINDOW) {
                    break;
                }
            }
        }
        if (v.isEmpty()) {
            return "not-run";
        }
        int flips = 0;
        for (int i = 0; i + 1 < v.size(); i++) {
            if (!v.get(i).equals(v.get(i + 1))) {
                flips++;
            }
        }
        if (flips >= 3) {
            return "flaky";
        }
        if (!v.get(0)) {
            int leadingBad = 0;
            while (leadingBad < v.size() && !v.get(leadingBad)) {
                leadingBad++;
            }
            return leadingBad >= 2 ? "broken" : "regression";
        }
        return v.contains(Boolean.FALSE) ? "recovered" : "stable";
    }

    /**
     * Iteration identity, after checking the caller may read its project.
     *
     * <p>Squash's own ACL never sees these native queries, so the check has to happen here: on the
     * live instance most users are scoped to a single project out of 21.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> requireReadableIteration(long iterationId) {
        List<Object[]> rows =
                em.createNativeQuery(
                                """
                                select p.PROJECT_ID, p.NAME, cln.NAME, it.NAME, it.REFERENCE
                                from ITERATION it
                                join CAMPAIGN_ITERATION ci     on ci.ITERATION_ID = it.ITERATION_ID
                                join CAMPAIGN_LIBRARY_NODE cln on cln.CLN_ID = ci.CAMPAIGN_ID
                                join PROJECT p                 on p.PROJECT_ID = cln.PROJECT_ID
                                where it.ITERATION_ID = :it
                                """)
                        .setParameter("it", iterationId)
                        .getResultList();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown iteration");
        }
        Object[] r = rows.get(0);
        if (!new HashSet<>(readableProjectIds()).contains(num(r[0]))) {
            throw new AccessDeniedException("iteration outside the readable perimeter");
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("iterationId", iterationId);
        meta.put("project", r[1]);
        meta.put("campaign", r[2]);
        meta.put("iteration", r[3]);
        meta.put("reference", r[4]);
        return meta;
    }

    /** Projects the current user can read through the campaign library, where iterations live. */
    private List<Long> readableProjectIds() {
        return projectFinder.findReadableProjectIdsOnCampaignLibrary();
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }

    private long count(String table) {
        Number n = (Number) em.createNativeQuery("select count(*) from " + table).getSingleResult();
        return n.longValue();
    }
}
