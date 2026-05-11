/**
 * Core API for the Pudel Plugin Development Kit (PDK).
 *
 * <p>This package provides the fundamental interfaces and classes that plugins use to
 * interact with the Pudel Discord bot runtime. The main entry point for plugins is
 * the {@link group.worldstandard.pudel.api.PluginContext} interface, which provides
 * access to all bot services and Discord API functionality.</p>
 *
 * <h2>Key Components:</h2>
 * <ul>
 *   <li>{@link group.worldstandard.pudel.api.PluginContext} - Main context for accessing bot services</li>
 *   <li>{@link group.worldstandard.pudel.api.PluginInfo} - Plugin metadata container</li>
 *   <li>{@link group.worldstandard.pudel.api.PudelProperties} - Core bot properties and version info</li>
 * </ul>
 *
 * <h2>Getting Started:</h2>
 * <pre>{@code
 * @Plugin(name = "MyPlugin", version = "1.0.0", author = "Author")
 * public class MyPlugin {
 *
 *     @OnEnable
 *     public void onEnable(PluginContext context) {
 *         context.log("info", "Plugin enabled!");
 *     }
 * }
 * }</pre>
 *
 * @since 2.3.0
 */
package group.worldstandard.pudel.api;