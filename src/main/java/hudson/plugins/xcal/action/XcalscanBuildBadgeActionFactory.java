package hudson.plugins.xcal.action;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import hudson.plugins.xcal.util.XcalscanUtil;
import jenkins.model.TransientActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Extension
public class XcalscanBuildBadgeActionFactory extends TransientActionFactory<Run> {
    public static final Logger log = LoggerFactory.getLogger(XcalscanBuildBadgeActionFactory.class);

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @Override
    public Collection<? extends Action> createFor(@Nonnull Run run) {
        log.info("[createFor] run： " + run.getId());
        List<XcalscanAnalysisAction> actions = XcalscanUtil.getPersistentActions(run, XcalscanAnalysisAction.class);
        if (actions.isEmpty()) {
            log.info("[createFor] XcalscanAnalysisAction list size: " + actions.size());
            return Collections.emptyList();
        }

        String url = null;
        for (XcalscanAnalysisAction a : actions) {
            // with workflows, we don't have realtime access to build logs, so url might be null
            // it might also have failed, but we still want to show the wave
            log.info("[createFor] XcalscanAnalysisAction url: " + a.getUrl());
            if (a.getUrl() != null) {
                if (url == null) {
                    url = a.getUrl();
                } else if (!url.equals(a.getUrl())) {
                    // there are several different URLs, so we don't display any URL
                    url = null;
                    break;
                }
            }
        }
        return Collections.singletonList(new XcalscanBuildBadgeAction(url));
    }
}
