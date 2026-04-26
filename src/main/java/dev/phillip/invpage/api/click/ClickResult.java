package dev.phillip.invpage.api.click;

public sealed interface ClickResult {

    record Allow() implements ClickResult {}
    record Cancel() implements ClickResult {}
    record Close() implements ClickResult {}
    record Refresh() implements ClickResult {}
    record Navigate(int targetPage) implements ClickResult {}

    ClickResult ALLOW = new Allow();
    ClickResult CANCEL = new Cancel();
    ClickResult CLOSE = new Close();
    ClickResult REFRESH = new Refresh();

    static ClickResult allow() { return ALLOW; }
    static ClickResult cancel() { return CANCEL; }
    static ClickResult close() { return CLOSE; }
    static ClickResult refresh() { return REFRESH; }
    static ClickResult navigate(int targetPage) { return new Navigate(targetPage); }
}
