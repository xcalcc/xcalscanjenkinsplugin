package hudson.plugins.xcal;

import com.google.common.annotations.VisibleForTesting;
import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.plugins.xcal.config.XcalscanServer;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;


@Extension(ordinal = 100)
public class XcalscanGlobalConfiguration extends GlobalConfiguration {
    public static final Logger log = LoggerFactory.getLogger(XcalscanGlobalConfiguration.class);

    private final Supplier<Jenkins> jenkinsSupplier;

    @CopyOnWrite
    private volatile XcalscanServer[] scanServers = new XcalscanServer[0];

    public XcalscanGlobalConfiguration() {
        this(
                () -> Optional.ofNullable(Jenkins.getInstanceOrNull())
                        .orElseThrow(() -> new IllegalStateException("Could not get Jenkins instance"))
        );
    }

    @VisibleForTesting
    public XcalscanGlobalConfiguration(Supplier<Jenkins> supplier) {
        load();
        this.jenkinsSupplier = supplier;
    }

    /**
     * @return all configured {@link XcalscanServer}
     */
    public XcalscanServer[] getScanServers() {
        return Arrays.copyOf(scanServers, scanServers.length);
    }

    public void setScanServers(XcalscanServer... scanServers) {
        this.scanServers = scanServers;
        save();
    }


    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        List<XcalscanServer> list = req.bindJSONToList(XcalscanServer.class, json.get("server"));
        setScanServers(list.toArray(new XcalscanServer[0]));
        return true;
    }

    public FormValidation doCheckName(@QueryParameter String value) {
        return StringUtils.isBlank(value) ? FormValidation.error(Messages.XcalscanGlobalConfiguration_DescriptorImpl_errors_emptyName()) : FormValidation.ok();
    }

    public FormValidation doCheckPort(@QueryParameter String value) {
        FormValidation result;
        log.info("[doCheckPort] port value: {}", value);
        if (Pattern.compile("^[0-9]{1,5}$").matcher(value).find()) {
            result = FormValidation.ok();
        } else {
            result = FormValidation.error(Messages.XcalscanBuilder_DescriptorImpl_errors_validPort());
        }
        return result;
    }

    public FormValidation doCheckServerAddress(@QueryParameter String value) {
        log.info("[doCheckServerAddress]");
        if (value.length() == 0) {
            return FormValidation.error(Messages.XcalscanBuilder_DescriptorImpl_errors_validServerAddress());
        }
        if (value.length() < 4) {
            return FormValidation.warning(Messages.XcalscanBuilder_DescriptorImpl_warnings_reallyServerAddress());
        }
        return FormValidation.ok();
    }

    public static XcalscanGlobalConfiguration get() {
        return GlobalConfiguration.all().get(XcalscanGlobalConfiguration.class);
    }
}
