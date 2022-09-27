
package hudson.plugins.xcal.action;

import hudson.model.ProminentProjectAction;
import hudson.plugins.xcal.Messages;

public final class XcalscanProjectIconAction implements ProminentProjectAction {
    private final XcalscanAnalysisAction xcalscanAnalysisAction;

    public XcalscanProjectIconAction() {
        this.xcalscanAnalysisAction = null;
    }

    public XcalscanProjectIconAction(XcalscanAnalysisAction xcalscanAnalysisAction) {
        this.xcalscanAnalysisAction = xcalscanAnalysisAction;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/xcalscan-jenkins/images/xcal_large.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.XcalscanAction_Xcalscan();
    }

    @Override
    public String getUrlName() {
        return xcalscanAnalysisAction != null ? xcalscanAnalysisAction.getUrl() : null;
    }

}
