package shopnest.breaker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * A small circuit breaker with the same settings as the one from class.
 *
 * How a caller uses it:
 *     if (breaker.allowCall()) {
 *         // make the call, then tell the breaker how it went:
 *         breaker.recordSuccess();   // or breaker.recordFailure();
 *     } else {
 *         // use the simple answer (the fallback), and do not call the friend
 *     }
 */
public class CircuitBreaker {

    // The settings (the same numbers as in class). Do not change them.
    static final int WINDOW_SIZE = 5;        // in CLOSED we look at the last 5 calls
    static final int FAILURE_PERCENT = 50;   // 50 percent or more failing is "too many"
    static final long WAIT_MILLIS = 15_000;  // how long the breaker stays OPEN: 15 seconds
    static final int TEST_CALLS = 3;         // how many test calls HALF_OPEN lets through

    private final LongSupplier clock;        // the time now, in milliseconds (the tests use a pretend clock)

    private State state = State.CLOSED;
    private final Deque<Boolean> results = new ArrayDeque<>(); // the latest results: true = that call failed
    private long openedAt;                   // the time when the breaker went OPEN
    private int testCallsGiven;              // test calls let through so far, while HALF_OPEN

    public CircuitBreaker(LongSupplier clock) {
        this.clock = clock;
    }

    public State state() {
        return state;
    }

    public void recordSuccess() {
        recordResult(false);
    }

    public void recordFailure() {
        recordResult(true);
    }

    // ------------------------------------------------------------------
    // Already written for you. You can use these in your two tasks.
    // ------------------------------------------------------------------

    /** True when the failures are 50 percent or more of the calls. Whole numbers only. 0 calls is never "too many". */
    static boolean tooManyFailures(int failures, int calls) {
        return calls > 0 && failures * 100 >= FAILURE_PERCENT * calls;
    }

    /** How many of the results in the list are failures. */
    private int failureCount() {
        int count = 0;
        for (boolean failed : results) {
            if (failed) {
                count++;
            }
        }
        return count;
    }

    /** Go to OPEN: remember the time, and start with an empty list of results. */
    private void toOpen() {
        state = State.OPEN;
        openedAt = clock.getAsLong();
        results.clear();
    }

    /** Go to HALF_OPEN: no results yet, and no test call has been given yet. */
    private void toHalfOpen() {
        state = State.HALF_OPEN;
        results.clear();
        testCallsGiven = 0;
    }

    /** Go back to CLOSED, with an empty list of results. */
    private void toClosed() {
        state = State.CLOSED;
        results.clear();
    }

    // ------------------------------------------------------------------
    // Task 1: may this call go to the friend?
    // ------------------------------------------------------------------
    public boolean allowCall() {
        // TODO 1: answer true or false, by the state:
        //   CLOSED    -> true.
        //   OPEN      -> if MORE than WAIT_MILLIS have passed since openedAt, call toHalfOpen()
        //                and carry on as HALF_OPEN (this call is the first test call).
        //                Otherwise -> false.
        //   HALF_OPEN -> true for the first TEST_CALLS calls (count them in testCallsGiven), then false.
        return false; // this line is only a placeholder: replace it with your code
    }

    // ------------------------------------------------------------------
    // Task 2: a call is finished. How did it go?
    // ------------------------------------------------------------------
    private void recordResult(boolean failed) {
        // TODO 2: use the list "results" (true = failed):
        //   OPEN      -> do nothing (a late result is ignored).
        //   CLOSED    -> add the result. Keep only the last WINDOW_SIZE results (drop the oldest).
        //                When there are WINDOW_SIZE results and tooManyFailures(...) says yes -> toOpen().
        //   HALF_OPEN -> add the result. When there are TEST_CALLS results:
        //                tooManyFailures(...) says yes -> toOpen(), otherwise -> toClosed().
    }
}
