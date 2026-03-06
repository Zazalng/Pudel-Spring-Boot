package group.worldstandard.pudel.api;

/**
 * Provides core properties about the Pudel bot runtime.
 * <p>
 * Plugins can access this via {@link PluginContext#getPudel()} to check
 * the core version, codename, or user agent string.
 * <p>
 * Example usage in a {@code @Plugin} class:
 * <pre>
 * {@code @OnEnable}
 * public void onEnable(PluginContext context) {
 *     PudelProperties pudel = context.getPudel();
 *     context.log("info", "Running on Pudel " + pudel.getVersion()
 *         + " (" + pudel.getCodename() + ")");
 * }
 * </pre>
 */
public interface PudelProperties {

    /**
     * Gets the bot name (e.g., "Pudel").
     *
     * @return the bot name
     */
    String getName();

    /**
     * Gets the semantic version of the core (e.g., "2.2.0").
     *
     * @return the version string
     */
    String getVersion();

    /**
     * Gets the release codename (e.g., "Schnauzer").
     *
     * @return the codename string
     */
    String getCodename();

    /**
     * Gets the HTTP user-agent string used for outgoing requests.
     *
     * @return the user-agent string
     */
    String getUserAgent();
}
