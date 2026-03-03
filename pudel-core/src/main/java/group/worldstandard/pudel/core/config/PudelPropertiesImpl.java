package group.worldstandard.pudel.core.config;

import group.worldstandard.pudel.api.PudelProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Pudel branding.
 */
@Component
@ConfigurationProperties(prefix = "pudel.branding")
public class PudelPropertiesImpl implements PudelProperties {
    private String name;
    private String codename;
    private String version;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getCodename() {
        return codename;
    }

    public void setCodename(String codename) {
        this.codename = codename;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getUserAgent(){
        return "%s (v%s) - %s".formatted(name, version, codename);
    }
}
