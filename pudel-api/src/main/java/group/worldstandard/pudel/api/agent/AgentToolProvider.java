/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.api.agent;

/**
 * Interface for classes that provide agent tools.
 * <p>
 * Plugins can implement this interface and register instances with
 * the {@link AgentToolRegistry} to make tools available to the AI agent.
 * <p>
 * Example:
 * <pre>
 * public class WeatherTools implements AgentToolProvider {
 *
 *     &#064;AgentTool(
 *         name = "get_weather",
 *         description = "Get weather for a location"
 *     )
 *     public String getWeather(AgentToolContext ctx, String location) {
 *         return "Weather in " + location + ": Sunny, 25°C";
 *     }
 *
 *     &#064;AgentTool(
 *         name = "get_forecast",
 *         description = "Get weather forecast for upcoming days"
 *     )
 *     public String getForecast(AgentToolContext ctx, String location, int days) {
 *         return "Forecast for " + location + " (" + days + " days): ...";
 *     }
 * }
 * </pre>
 * <p>
 * Then register in your plugin's onEnable:
 * <pre>
 *      &#064;Override
 *      public void onEnable(PluginContext context) {
 *          context.getAgentToolRegistry().registerProvider("my-plugin", new WeatherTools());
 *      }
 * </pre>
 */
public interface AgentToolProvider {

    /**
     * Called when the provider is registered.
     * Override to perform initialization.
     */
    default void onRegister() {
        // Default no-op
    }

    /**
     * Called when the provider is unregistered.
     * Override to perform cleanup.
     */
    default void onUnregister() {
        // Default no-op
    }

    /**
     * Get a friendly name for this tool provider.
     * Used for logging and debugging.
     * @return provider name
     */
    default String getProviderName() {
        return getClass().getSimpleName();
    }
}
