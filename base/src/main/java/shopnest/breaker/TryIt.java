package shopnest.breaker;

import java.util.function.Supplier;

/**
 * Run this file to check your work. It does not change anything: it only shows what your breaker does.
 * It uses a pretend clock, so nobody waits 15 real seconds.
 */
public class TryIt {

    static long now = 0; // the pretend clock, in milliseconds

    public static void main(String[] args) {
        System.out.println("Scenario 1: a friend that gets well");
        run(() -> scenario1());
        System.out.println("Scenario 2: a friend that is still sick");
        run(() -> scenario2());
        System.out.println("Scenario 3: only the last 5 calls count");
        run(() -> scenario3());
    }

    static void scenario1() {
        now = 0;
        CircuitBreaker b = new CircuitBreaker(() -> now);
        show("a new breaker, its state", () -> b.state());
        show("allowCall()", () -> b.allowCall());
        failures(b, 4);
        show("after 4 failures, the state", () -> b.state());
        b.recordSuccess();
        show("the 5th call succeeds (4 of 5 failed)", () -> b.state());
        show("allowCall() at 0 ms", () -> b.allowCall());
        now = 15_000;
        show("allowCall() at 15000 ms (not MORE)", () -> b.allowCall());
        now = 15_001;
        show("allowCall() at 15001 ms", () -> b.allowCall());
        show("the state now", () -> b.state());
        show("test call 2", () -> b.allowCall());
        show("test call 3", () -> b.allowCall());
        show("test call 4 (no result yet)", () -> b.allowCall());
        b.recordSuccess();
        b.recordFailure();
        show("after 2 results (success, failure)", () -> b.state());
        b.recordSuccess();
        show("the 3rd result is a success (1 of 3 failed)", () -> b.state());
        show("allowCall()", () -> b.allowCall());
    }

    static void scenario2() {
        now = 1_000;
        CircuitBreaker b = new CircuitBreaker(() -> now);
        failures(b, 5);
        show("after 5 failures, the state", () -> b.state());
        now = 16_001;
        show("allowCall() at 16001 ms", () -> b.allowCall());
        show("test call 2", () -> b.allowCall());
        show("test call 3", () -> b.allowCall());
        b.recordFailure();
        b.recordFailure();
        b.recordSuccess();
        show("the results: failure, failure, success", () -> b.state());
        show("allowCall() at 16001 ms", () -> b.allowCall());
        now = 31_001;
        show("allowCall() at 31001 ms (15000 later)", () -> b.allowCall());
        now = 31_002;
        show("allowCall() at 31002 ms", () -> b.allowCall());
    }

    static void scenario3() {
        now = 0;
        CircuitBreaker b = new CircuitBreaker(() -> now);
        for (int i = 0; i < 3; i++) {
            b.recordSuccess();
        }
        failures(b, 2);
        show("3 successes, then 2 failures (2 of 5)", () -> b.state());
        b.recordFailure();
        show("one more failure (last 5: 3 failed)", () -> b.state());
    }

    // ---- small helpers: you do not need to read them ----

    static void failures(CircuitBreaker b, int times) {
        for (int i = 0; i < times; i++) {
            b.recordFailure();
        }
    }

    static void show(String label, Supplier<Object> what) {
        String text;
        try {
            text = String.valueOf(what.get());
        } catch (RuntimeException e) {
            text = "something is wrong -> " + e;
        }
        System.out.println(String.format("  %-45s: %s", label, text));
    }

    static void run(Runnable scenario) {
        try {
            scenario.run();
        } catch (RuntimeException e) {
            System.out.println("  something is wrong -> " + e);
        }
    }
}
