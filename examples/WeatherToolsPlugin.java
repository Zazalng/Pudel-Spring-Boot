/*
 * Pudel - A Moderate Discord Chat Bot
 * Example Plugin: Weather Tools
 *
 * This example demonstrates how to create agent tools that the AI can use.
 *
 * Note: This is an EXAMPLE file - it won't compile without a real weather API.
 * Use it as a template for creating your own plugin tools.
 */
package com.example.weatherplugin;

import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.PluginInfo;
import worldstandard.group.pudel.api.SimplePlugin;
import worldstandard.group.pudel.api.agent.AgentTool;
import worldstandard.group.pudel.api.agent.AgentToolContext;
import worldstandard.group.pudel.api.agent.AgentToolProvider;

/**
 * Example plugin that adds weather-related tools to Pudel's AI agent.
 *
 * When a user asks Pudel about the weather, the agent will be able to
 * use these tools to fetch and display weather information.
 */
public class WeatherPlugin extends SimplePlugin {

    private WeatherTools weatherTools;

    @Override
    public PluginInfo getPluginInfo() {
        return PluginInfo.builder()
                .name("WeatherPlugin")
                .version("1.0.0")
                .author("Example Author")
                .description("Adds weather-related tools to Pudel's AI agent")
                .build();
    }

    @Override
    public void onEnable(PluginContext context) {
        // Create our tool provider
        weatherTools = new WeatherTools();

        // Register with the agent tool registry
        int registered = context.getAgentToolRegistry().registerProvider(
                getPluginInfo().getName(),
                weatherTools
        );

        context.log("info", "Registered " + registered + " weather tools");
    }

    @Override
    public void onDisable(PluginContext context) {
        // Unregister all our tools
        int unregistered = context.getAgentToolRegistry().unregisterAll(getPluginInfo().getName());
        context.log("info", "Unregistered " + unregistered + " weather tools");
    }

    /**
     * Tool provider class containing @AgentTool annotated methods.
     *
     * Each method annotated with @AgentTool becomes available to the AI agent.
     * The agent will decide when to use these tools based on the user's request
     * and the tool descriptions.
     */
    public static class WeatherTools implements AgentToolProvider {

        @Override
        public String getProviderName() {
            return "Weather Tools";
        }

        /**
         * Get current weather for a location.
         *
         * The AI will call this when a user asks about current weather.
         * Example user message: "What's the weather like in Tokyo?"
         */
        @AgentTool(
            name = "get_weather",
            description = "Get the current weather conditions for a specific location",
            keywords = {"weather", "temperature", "forecast", "climate", "hot", "cold", "rain"}
        )
        public String getWeather(AgentToolContext context, String location) {
            // In a real plugin, you would call a weather API here
            // For example: WeatherAPI.getCurrentWeather(location)

            // Mock response for demonstration:
            return String.format(
                "Current weather in %s:\n" +
                "🌡️ Temperature: 25°C (77°F)\n" +
                "☀️ Conditions: Partly cloudy\n" +
                "💨 Wind: 15 km/h NE\n" +
                "💧 Humidity: 65%%",
                location
            );
        }

        /**
         * Get weather forecast for upcoming days.
         *
         * Example user message: "What will the weather be like in London next week?"
         */
        @AgentTool(
            name = "get_forecast",
            description = "Get weather forecast for the next few days",
            keywords = {"forecast", "tomorrow", "next week", "upcoming", "predict"}
        )
        public String getForecast(AgentToolContext context, String location, int days) {
            // Limit days to reasonable range
            days = Math.min(Math.max(days, 1), 7);

            // Mock response:
            StringBuilder forecast = new StringBuilder();
            forecast.append("Weather forecast for ").append(location).append(":\n\n");

            String[] conditions = {"☀️ Sunny", "⛅ Partly cloudy", "☁️ Cloudy", "🌧️ Rainy"};
            int[] temps = {24, 22, 20, 18, 23, 25, 21};

            for (int i = 0; i < days; i++) {
                forecast.append("Day ").append(i + 1).append(": ")
                        .append(conditions[i % conditions.length])
                        .append(", ").append(temps[i % temps.length]).append("°C\n");
            }

            return forecast.toString();
        }

        /**
         * Check if it's a good day for outdoor activities.
         *
         * Example: "Is it good weather for a picnic today?"
         */
        @AgentTool(
            name = "check_outdoor_conditions",
            description = "Check if weather conditions are suitable for outdoor activities",
            keywords = {"outdoor", "picnic", "hiking", "outside", "activity", "suitable"}
        )
        public String checkOutdoorConditions(AgentToolContext context, String location, String activity) {
            // Mock response:
            return String.format(
                "Outdoor conditions in %s for %s:\n" +
                "✅ Good conditions!\n" +
                "- Temperature is comfortable (22°C)\n" +
                "- Low chance of rain (10%%)\n" +
                "- Light wind\n\n" +
                "Recommendation: Great day for %s!",
                location, activity, activity
            );
        }

        /**
         * Guild-only tool example - get weather alerts for a server.
         */
        @AgentTool(
            name = "get_weather_alerts",
            description = "Get severe weather alerts for the server's configured region",
            keywords = {"alert", "warning", "severe", "storm", "emergency"},
            guildOnly = true
        )
        public String getWeatherAlerts(AgentToolContext context) {
            // This tool is only available in guild channels, not DMs
            long guildId = context.getGuildId();

            // Mock response:
            return "No severe weather alerts for this region. All clear! ☀️";
        }
    }
}
