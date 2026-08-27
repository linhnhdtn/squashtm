package org.squashtest.tm.plugin.dtnmyfeature;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private long count(String table) {
        Number n = (Number) em.createNativeQuery("select count(*) from " + table).getSingleResult();
        return n.longValue();
    }
}
