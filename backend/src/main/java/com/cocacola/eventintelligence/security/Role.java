package com.cocacola.eventintelligence.security;

import java.util.Set;

public enum Role {
    ADMINISTRADOR, ORGANIZADOR, MARKETING;

    public Set<String> permissions() {
        return switch (this) {
            case ADMINISTRADOR -> Set.of("dashboard", "events.read", "events.manage", "participants.read", "operations", "reports", "powerbi", "users.manage");
            case ORGANIZADOR -> Set.of("dashboard", "events.read", "participants.read", "operations");
            case MARKETING -> Set.of("dashboard", "events.read", "participants.read", "reports", "powerbi");
        };
    }
}
