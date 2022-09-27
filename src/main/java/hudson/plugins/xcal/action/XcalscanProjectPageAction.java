
package hudson.plugins.xcal.action;

import hudson.model.InvisibleAction;
import hudson.model.ProminentProjectAction;

import java.util.List;

/**
 * Displays a jelly section in the Project page with information regarding Scan summary
 * This is recreated every time something is loaded, so should be lightweight
 */
public class XcalscanProjectPageAction extends InvisibleAction implements ProminentProjectAction {
    private final List<XcalscanAnalysisAction> xcalscanAnalysisActions;

    public XcalscanProjectPageAction(List<XcalscanAnalysisAction> xcalscanAnalysisActions) {
        this.xcalscanAnalysisActions = xcalscanAnalysisActions;
    }

    /**
     * Called while building the jelly section
     */
    public List<XcalscanAnalysisAction> getXcalscanAnalysisActions() {
        return xcalscanAnalysisActions;
    }
}
