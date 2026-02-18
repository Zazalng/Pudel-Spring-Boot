/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.agent;

import group.worldstandard.pudel.api.agent.AgentToolContext;
import net.dv8tion.jda.api.entities.Member;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of AgentToolContext.
 */
public class AgentToolContextImpl implements AgentToolContext {

    private final long targetId;
    private final boolean isGuild;
    private final long requestingUserId;
    private final Member requestingMember;
    private final Map<String, Object> contextData;

    public AgentToolContextImpl(long targetId, boolean isGuild, long requestingUserId) {
        this(targetId, isGuild, requestingUserId, null, Collections.emptyMap());
    }

    public AgentToolContextImpl(long targetId, boolean isGuild, long requestingUserId, Map<String, Object> contextData) {
        this(targetId, isGuild, requestingUserId, null, contextData);
    }

    public AgentToolContextImpl(long targetId, boolean isGuild, long requestingUserId,
                                Member requestingMember, Map<String, Object> contextData) {
        this.targetId = targetId;
        this.isGuild = isGuild;
        this.requestingUserId = requestingUserId;
        this.requestingMember = requestingMember;
        this.contextData = contextData != null ? new HashMap<>(contextData) : new HashMap<>();
    }

    @Override
    public long getTargetId() {
        return targetId;
    }

    @Override
    public boolean isGuild() {
        return isGuild;
    }

    @Override
    public long getRequestingUserId() {
        return requestingUserId;
    }

    @Override
    public Member getRequestingMember() {
        return requestingMember;
    }

    @Override
    public Map<String, Object> getContextData() {
        return Collections.unmodifiableMap(contextData);
    }

    /**
     * Builder for AgentToolContextImpl.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long targetId;
        private boolean isGuild;
        private long requestingUserId;
        private Member requestingMember;
        private final Map<String, Object> contextData = new HashMap<>();

        public Builder targetId(long targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder isGuild(boolean isGuild) {
            this.isGuild = isGuild;
            return this;
        }

        public Builder requestingUserId(long requestingUserId) {
            this.requestingUserId = requestingUserId;
            return this;
        }

        public Builder requestingMember(Member requestingMember) {
            this.requestingMember = requestingMember;
            return this;
        }

        public Builder contextData(String key, Object value) {
            this.contextData.put(key, value);
            return this;
        }

        public Builder contextData(Map<String, Object> data) {
            this.contextData.putAll(data);
            return this;
        }

        public AgentToolContextImpl build() {
            return new AgentToolContextImpl(targetId, isGuild, requestingUserId, requestingMember, contextData);
        }
    }
}
