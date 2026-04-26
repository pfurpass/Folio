package dev.phillip.invpage.api.click;

import java.time.Duration;

public record CooldownPolicy(Duration duration, Scope scope) {

    public enum Scope { PER_PLAYER, GLOBAL }

    public static CooldownPolicy perPlayer(Duration d) { return new CooldownPolicy(d, Scope.PER_PLAYER); }
    public static CooldownPolicy global(Duration d) { return new CooldownPolicy(d, Scope.GLOBAL); }
    public long millis() { return duration.toMillis(); }
}
