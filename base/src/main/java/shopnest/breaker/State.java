package shopnest.breaker;

/** The three states of the breaker. Already finished: do not change this file. */
public enum State {
    CLOSED,     // normal: calls go through, and we count the failures
    OPEN,       // too many failures: calls are refused at once
    HALF_OPEN   // the wait is over: a few test calls go through
}
