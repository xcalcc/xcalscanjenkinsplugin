package hudson.plugins.xcal.action;

import hudson.model.BuildBadgeAction;
import hudson.plugins.xcal.Messages;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * {@link BuildBadgeAction} that shows the build contains Xcalscan analysis.
 */
@ExportedBean
public final class XcalscanBuildBadgeAction implements BuildBadgeAction {

    private final String url;

    public XcalscanBuildBadgeAction(String url) {
        this.url = url;
    }

    public String getTooltip() {
        return Messages.BuildXcalscanAction_Tooltip();
    }

    @Override
    public String getDisplayName() {
        return Messages.XcalscanAction_Xcalscan();
    }

    // non use interface methods
    @Override
    public String getIconFileName() {
        return "/plugin/xcalscan-jenkins/images/xcal_small.png";
    }

    @Override
    public String getUrlName() {
        return url;
    }

    @Exported(visibility = 2)
    public String getUrl() {
        return url;
    }
}
