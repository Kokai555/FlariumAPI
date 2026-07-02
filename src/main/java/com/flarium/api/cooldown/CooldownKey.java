package com.flarium.api.cooldown;

import java.util.UUID;

public record CooldownKey(UUID uuid, String namespace) {
    public static CooldownKey of(UUID uuid, String namespace) {
        return new CooldownKey(uuid, namespace);
    }
}