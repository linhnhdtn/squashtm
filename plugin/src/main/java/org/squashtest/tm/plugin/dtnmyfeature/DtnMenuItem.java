package org.squashtest.tm.plugin.dtnmyfeature;

import org.squashtest.tm.api.security.acls.AccessRule;
import org.squashtest.tm.api.security.acls.Permissions;
import org.squashtest.tm.api.widget.MenuItem;
import org.squashtest.tm.api.widget.TreeNodeType;
import org.squashtest.tm.api.widget.access.AccessRuleBuilder;

class DtnMenuItem implements MenuItem {

    /**
     * Rule deciding when the menu entry is clickable.
     *
     * <p>It MUST be a node-selection rule. Two traps here:
     *
     * <ul>
     *   <li>{@code AccessRuleBuilder.anybody()} returns an empty Anybody bean that Jackson cannot
     *       serialize, which breaks the whole /backend/referential endpoint.
     *   <li>{@code null} serializes fine but the front end treats it as "never allowed" ({@code if
     *       (accessRule == null) return false}), so the entry stays greyed out forever.
     * </ul>
     */
    private final AccessRule accessRule =
            AccessRuleBuilder.singleNodeSelection()
                    .nodePermission(TreeNodeType.LIBRARY, Permissions.READ)
                    .or()
                    .nodePermission(TreeNodeType.CAMPAIGN, Permissions.READ)
                    .or()
                    .nodePermission(TreeNodeType.FOLDER, Permissions.READ)
                    .build();

    @Override
    public String getLabel() {
        return "DTN Dashboard";
    }

    @Override
    public String getTooltip() {
        return "Bang dieu khien noi bo DTN";
    }

    @Override
    public String getUrl() {
        return "plugin/dtn-myfeature/index";
    }

    @Override
    public AccessRule getAccessRule() {
        return accessRule;
    }
}
