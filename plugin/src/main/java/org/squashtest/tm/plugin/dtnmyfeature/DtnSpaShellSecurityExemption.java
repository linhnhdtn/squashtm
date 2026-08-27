package org.squashtest.tm.plugin.dtnmyfeature;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import org.squashtest.tm.api.security.authentication.SecurityExemptionEndPoint;

/**
 * Lets anonymous requests reach /dtn-dashboard, exactly like the core SPA routes.
 *
 * <p>Squash serves the SPA shell without authentication (/home-workspace answers 200 when logged
 * out) and lets the Angular app redirect to the login page. Without this exemption our route
 * answered 401, so a bookmark or an F5 gave an error instead of the login screen. Only the shell is
 * public: the REST endpoints under /plugin/dtn-myfeature/api stay behind @PreAuthorize.
 */
@Component
public class DtnSpaShellSecurityExemption implements SecurityExemptionEndPoint {

    private static final String SPA_SHELL_PATTERN = "/dtn-dashboard/**";

    @Override
    public List<String> getIgnoreAuthUrlPatterns() {
        return Collections.singletonList(SPA_SHELL_PATTERN);
    }

    @Override
    public List<String> getIgnoreCsrfUrlPatterns() {
        return Collections.emptyList();
    }
}
