package org.squashtest.tm.plugin.dtnmyfeature;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Server-side routing for the plugin.
 *
 * <p>Squash TM maps every SPA route explicitly (see the AngularAppPageUrls enum in the core:
 * /home-workspace/**, /campaign-workspace/** ...), so a route added to the Angular app is reachable
 * by clicking a link but returns 404 when the URL is typed or refreshed. Registering the same
 * forward here fixes deep links without patching the core.
 */
@Configuration
public class DtnMyFeatureWebMvcConfig implements WebMvcConfigurer {

    /** Thymeleaf view of the SPA shell (META-INF/resources/index.html), same value the core uses. */
    private static final String SPA_INDEX = "/index";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // standalone pages shipped inside this jar
        registry.addViewController("/plugin/dtn-myfeature/index")
                .setViewName("forward:/plugin/dtn-myfeature/index.html");
        registry.addViewController("/plugin/dtn-myfeature/iteration-report")
                .setViewName("forward:/plugin/dtn-myfeature/iteration-report.html");

        // page that lives inside the SPA: deep link + F5 must serve the SPA shell
        registry.addViewController("/dtn-dashboard").setViewName(SPA_INDEX);
        registry.addViewController("/dtn-dashboard/**").setViewName(SPA_INDEX);
    }
}
