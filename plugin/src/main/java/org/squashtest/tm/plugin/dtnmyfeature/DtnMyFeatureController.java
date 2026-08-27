package org.squashtest.tm.plugin.dtnmyfeature;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
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
     * Action word suggestions for the BDD step editor.
     *
     * <p>Squash ships this feature already (POST /backend/keyword-test-cases/autocomplete) but its
     * controller takes an Optional&lt;ActionWordService&gt; whose only implementation lives in the
     * proprietary premium plugin; without it the endpoint throws AccessDeniedException and the front
     * end falls back to a plain text input. The ACTION_WORD tables are core schema and already
     * populated by every step typed, so we read them directly.
     *
     * <p>An action word carries no keyword of its own (GIVEN/WHEN/... lives on KEYWORD_TEST_STEP), and
     * the same wording is commonly reused under several of them. We therefore suggest distinct
     * (keyword, action) pairs -- "But lbeo3" and "Given lbeo3" are two separate suggestions -- so
     * picking one restores the whole step, keyword included.
     */
    @GetMapping("/test-case/{testCaseId}/action-words")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> actionWords(
            @PathVariable long testCaseId, @RequestParam(name = "q", defaultValue = "") String q) {
        // ponytail: string_agg/ilike are PostgreSQL only, port to group_concat/lower(..) like if MariaDB is ever used
        List<Object[]> rows =
                em.createNativeQuery(
                                """
                                select distinct kts.keyword, s.action
                                from KEYWORD_TEST_STEP kts
                                join (
                                  select aw.action_word_id as id,
                                         string_agg(coalesce(t.text, '<' || p.name || '>'), '' order by f.fragment_order) as action
                                  from ACTION_WORD aw
                                  join ACTION_WORD_FRAGMENT f on f.action_word_id = aw.action_word_id
                                  left join ACTION_WORD_TEXT t on t.action_word_fragment_id = f.action_word_fragment_id
                                  left join ACTION_WORD_PARAMETER p on p.action_word_fragment_id = f.action_word_fragment_id
                                  where aw.project_id = (select project_id from TEST_CASE_LIBRARY_NODE where tcln_id = :tcid)
                                  group by aw.action_word_id
                                ) s on s.id = kts.action_word_id
                                where s.action ilike :q
                                order by 2, 1
                                limit 15
                                """)
                        .setParameter("tcid", testCaseId)
                        .setParameter("q", "%" + q + "%")
                        .getResultList();

        return rows.stream()
                .map(r -> Map.of("keyword", (String) r[0], "action", (String) r[1]))
                .toList();
    }

    private long count(String table) {
        Number n = (Number) em.createNativeQuery("select count(*) from " + table).getSingleResult();
        return n.longValue();
    }
}
