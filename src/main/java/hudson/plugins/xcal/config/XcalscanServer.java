package hudson.plugins.xcal.config;

import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.plugins.xcal.Messages;
import hudson.plugins.xcal.XcalscanGlobalConfiguration;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import java.io.Serializable;

@Getter
@Setter
public class XcalscanServer implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String name;
    private final String serverAddress;
    private final String port;
    private final String credential;

    @DataBoundConstructor
    public XcalscanServer(@CheckForNull String name, @CheckForNull String serverAddress, @CheckForNull String port, @CheckForNull String credential) {
        this.name = name;
        this.serverAddress = serverAddress;
        this.port = port;
        this.credential = credential;
    }

    public static XcalscanServer[] all() {
        return XcalscanGlobalConfiguration.get().getScanServers();
    }

    public static boolean isValid(String scanServerName, TaskListener listener) {
        String failureMsg = validationMsg(scanServerName);
        if (failureMsg != null) {
            listener.fatalError(failureMsg);
            return false;
        }
        return true;
    }

    public static void checkValid(String scanServerName) throws AbortException {
        String failureMsg = validationMsg(scanServerName);
        if (failureMsg != null) {
            throw new AbortException(failureMsg);
        }
    }

    private static String validationMsg(String scanServerName) {
        String failureMsg;
        XcalscanServer xcalscanServer = XcalscanServer.get(scanServerName);
        if (xcalscanServer == null) {
            if (StringUtils.isBlank(scanServerName)) {
                failureMsg = Messages.XcalscanServer_NoServer(XcalscanServer.all().length);
            } else {
                failureMsg = Messages.XcalscanServer_NoMatchServer(scanServerName, XcalscanServer.all().length);
            }
        } else {
            failureMsg = null;
        }
        return failureMsg;
    }

    public static XcalscanServer get(String name) {
        XcalscanServer[] availableServers = all();
        if (StringUtils.isEmpty(name) && availableServers.length > 0) {
            return availableServers[0];
        }
        for (XcalscanServer server : availableServers) {
            if (StringUtils.equals(name, server.getName())) {
                return server;
            }
        }
        return null;
    }
}
