package org.squashtest.tm.plugin.dtnmyfeature;

import org.springframework.stereotype.Component;
import org.squashtest.tm.api.plugin.EntityReference;
import org.squashtest.tm.api.plugin.PluginType;
import org.squashtest.tm.api.widget.MenuItem;
import org.squashtest.tm.api.wizard.WorkspaceWizard;
import org.squashtest.tm.api.workspace.WorkspaceType;

@Component
public class DtnMyFeaturePlugin implements WorkspaceWizard {

    private final MenuItem menu = new DtnMenuItem();

    @Override
    public String getId() {
        return "dtn-myfeature";
    }

    @Override
    public String getName() {
        return "DTN My Feature";
    }

    @Override
    public String getType() {
        return "DTN";
    }

    @Override
    public PluginType getPluginType() {
        return PluginType.WIZARD;
    }

    @Override
    public void validate(EntityReference reference) {
        // nothing to validate: the plugin needs no per-project configuration
    }

    @Override
    public WorkspaceType getConfiguringWorkspace() {
        return WorkspaceType.CAMPAIGN_WORKSPACE;
    }

    /**
     * Must be CAMPAIGN_WORKSPACE or REQUIREMENT_WORKSPACE.
     *
     * <p>Only those two workspace modules call initializeForWorkspace() in the front end, so a wizard
     * declaring TEST_CASE_WORKSPACE is registered and shipped in /backend/referential but no screen
     * ever renders its menu.
     */
    @Override
    public WorkspaceType getDisplayWorkspace() {
        return WorkspaceType.CAMPAIGN_WORKSPACE;
    }

    @Override
    public String getConfigurationPath(EntityReference context) {
        return "plugin/dtn-myfeature/index";
    }

    @Override
    public MenuItem getWizardMenu() {
        return menu;
    }
}
