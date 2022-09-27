package hudson.plugins.xcal.util;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.AbortException;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.xcal.action.XcalscanAnalysisAction;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


public final class XcalscanUtil {
    public static final Logger log = LoggerFactory.getLogger(XcalscanUtil.class);

    /**
     * Hide utility-class constructor.
     */
    private XcalscanUtil() {
    }

    public static <T extends Action> List<T> getPersistentActions(Actionable actionable, Class<T> type) {
        log.info("[getPersistentActions]");
        List<T> filtered = new LinkedList<>();
        // we use this method to avoid recursively calling transitive action factories
        for (Action a : actionable.getActions()) {
            if (a == null) {
                continue;
            }
            if (type.isAssignableFrom(a.getClass())) {
                filtered.add((T) a);
            }
        }
        return filtered;
    }

    /**
     * Collects as much information as it finds from the xcalscan in the build and adds it as an action to the build.
     * Even if no information is found, the action is added, marking in the build that a xcalscan ran.
     */
    public static void addBuildInfoTo(Run<?, ?> build, XcalscanAnalysisAction xcalscanAnalysisAction, boolean skippedIfNoBuild) {
        log.info("[addBuildInfoTo] run: " + build.getId() + ", xcalscanAnalysisAction.getScanTaskId(): " + xcalscanAnalysisAction.getScanTaskId());
        if (xcalscanAnalysisAction.getScanTaskId() != null) {
            build.addAction(xcalscanAnalysisAction);
        } else {
            addBuildInfoFromLastBuildTo(build, xcalscanAnalysisAction.getServerUrl(), skippedIfNoBuild);
        }
    }

    public static void addBuildInfoTo(Run<?, ?> build, XcalscanAnalysisAction xcalscanAnalysisAction) {
        log.info("[addBuildInfoTo]");
        addBuildInfoTo(build, xcalscanAnalysisAction, false);
    }

    public static void addBuildInfoFromLastBuildTo(Run<?, ?> build, String serverUrl, boolean isSkipped) {
        log.info("[addBuildInfoFromLastBuildTo]");
        Run<?, ?> previousBuild = build.getPreviousBuild();
        if (previousBuild == null) {
            addEmptyBuildInfo(build, serverUrl, isSkipped);
            return;
        }
        for (XcalscanAnalysisAction analysis : previousBuild.getActions(XcalscanAnalysisAction.class)) {
            if (analysis.getUrl() != null) {
                XcalscanAnalysisAction copy = XcalscanAnalysisAction.builder()
                        .url(analysis.getUrl())
                        .serverUrl(analysis.getServerUrl())
                        .isNew(false)
                        .isSkipped(isSkipped)
                        .build();
                build.addAction(copy);
                return;
            }
        }
        addEmptyBuildInfo(build, serverUrl, isSkipped);
    }

    public static void addEmptyBuildInfo(Run<?, ?> build, String serverUrl, boolean isSkipped) {
        log.info("[addEmptyBuildInfo] serverUrl: {}", serverUrl);
        XcalscanAnalysisAction analysis = XcalscanAnalysisAction.builder()
                .isNew(true)
                .isSkipped(false)
                .serverUrl(serverUrl)
                .build();
        analysis.setSkipped(isSkipped);
        build.addAction(analysis);
    }

    public static String getXcalscanCredential(StandardUsernamePasswordCredentials usernamePasswordCredentials) {
        log.info("[getXcalscanCredential] credential: {}", usernamePasswordCredentials.getId());
        String username = usernamePasswordCredentials.getUsername();
        String password = Secret.toString(usernamePasswordCredentials.getPassword());
        JSONObject params = new JSONObject();
        params.put("username", username);
        params.put("password", password);
        return params.toString();
    }

    public static StandardUsernamePasswordCredentials getCredentials(String credential) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        Jenkins.getInstanceOrNull(),
                        ACL.SYSTEM,
                        Collections.emptyList()
                ),
                CredentialsMatchers.allOf(
                        CredentialsMatchers.always(),
                        CredentialsMatchers.withId(credential)
                )

        );
    }

    public static void throwAbortException(TaskListener listener, String msg) throws AbortException {
        listener.fatalError(msg);
        throw new AbortException(msg);
    }
}
