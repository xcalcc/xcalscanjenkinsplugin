
package hudson.plugins.xcal.action;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import jenkins.model.TransientActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Extension
public class XcalscanProjectActionFactory extends TransientActionFactory<Job> {
    public static final Logger log = LoggerFactory.getLogger(XcalscanProjectActionFactory.class);

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @Override
    public Collection<? extends Action> createFor(Job project) {
        log.info("[createFor]");
        Set<String> urls = new HashSet<>();
        List<ProminentProjectAction> xcalscanProjectActions = new LinkedList<>();
        List<XcalscanAnalysisAction> filteredActions = new LinkedList<>();

        // don't fetch builds that haven't finished yet
        Run<?, ?> lastBuild = project.getLastCompletedBuild();

        if (lastBuild != null) {
            for (XcalscanAnalysisAction a : lastBuild.getActions(XcalscanAnalysisAction.class)) {
                if (a.getUrl() != null && !urls.contains(a.getUrl())) {
                    urls.add(a.getUrl());
                    xcalscanProjectActions.add(new XcalscanProjectIconAction(a));
                    filteredActions.add(a);
                }
            }
        }

        if (xcalscanProjectActions.isEmpty()) {
            // display at least 1 wave without any URL in the project page
            xcalscanProjectActions = Collections.emptyList();
        } else {
            XcalscanProjectPageAction projectPage = createProjectPage(lastBuild, filteredActions);
            if (projectPage != null) {
                xcalscanProjectActions.add(projectPage);
            }
        }
        return xcalscanProjectActions;
    }

    /**
     * Action that will create the jelly section in the Project page
     */
    @CheckForNull
    private XcalscanProjectPageAction createProjectPage(Run<?, ?> run, List<XcalscanAnalysisAction> actions) {
        log.info("[createProjectPage] actions size: {}", actions.size());
        List<XcalscanAnalysisAction> projects = new ArrayList<>();
        for (XcalscanAnalysisAction action : actions) {
            if (action.getServerUrl() != null && action.getProjectUUID() != null && action.getScanTaskId() != null) {
                projects.add(action);
            }
        }
        if (projects.isEmpty()) {
            return null;
        }
        return new XcalscanProjectPageAction(projects);
    }

}
