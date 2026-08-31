package org.squashtest.tm.plugin.dtnmyfeature;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PersistenceContext private EntityManager em;

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
                                order by 1, 2, 3
                                """)
                        .setParameter("tcid", testCaseId)
                        .getResultList();

        return assemble(rows, q);
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

    private long count(String table) {
        Number n = (Number) em.createNativeQuery("select count(*) from " + table).getSingleResult();
        return n.longValue();
    }
}
