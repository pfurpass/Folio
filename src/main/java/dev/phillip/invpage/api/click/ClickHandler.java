package dev.phillip.invpage.api.click;

@FunctionalInterface
public interface ClickHandler {
    ClickResult handle(ClickContext ctx);

    static ClickHandler noop() { return ctx -> ClickResult.cancel(); }
}
