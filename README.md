> **Where the files are:** the starter code (with the `TODO` notes) is in the `base/` folder of this branch (`main`). The finished code and the tests are on the branch `test-file`.

# The Circuit Breaker at ShopNest

## Problem Statement
ShopNest calls Groq to write a short pitch for each product. One day Groq is down. Every call waits, fails, and keeps a cashier busy for nothing. A **circuit breaker** is like the fuse (MCB) in your home: when too many calls fail, it **stops** the calls for a while, and then it checks again with a few test calls. In class, a library did this work for us. Now you write a small one yourself, in plain Java, so you see exactly what the three states do. The breaker does not call anything by itself. The caller asks `allowCall()` before each call, and tells the breaker how the call went with `recordSuccess()` or `recordFailure()`. You need about 19 lines of code, in 2 small tasks. There is no web server and no Spring, and nothing needs to be installed.

## Requirements
### The settings (already written as constants)
| Name | Value | What it means |
|---|---|---|
| `WINDOW_SIZE` | 5 | in CLOSED, we look at the last 5 results |
| `FAILURE_PERCENT` | 50 | 50 percent or more failing is "too many" |
| `WAIT_MILLIS` | 15000 | the breaker stays OPEN for 15 seconds |
| `TEST_CALLS` | 3 | HALF_OPEN lets 3 test calls through |

The breaker reads the time through `clock.getAsLong()` (milliseconds). In `TryIt` and in the tests the clock is pretend, so nobody waits for 15 real seconds.

### The three states
| State | `allowCall()` answers | What moves the breaker to another state |
|---|---|---|
| **CLOSED** | `true` | After each result, we keep only the last 5 results. When there are 5 results and **3 or more** failed (50 percent or more), go to **OPEN**. |
| **OPEN** | `false` | When a call arrives and **more than** 15000 ms have passed since the breaker went OPEN, go to **HALF_OPEN**. That call is test call number 1 and gets `true`. At exactly 15000 ms the answer is still `false`. The breaker does not watch the clock by itself: it only moves when a call arrives. |
| **HALF_OPEN** | `true` for the first 3 calls, then `false` | When 3 results are in: if 50 percent or more of them failed (2 or 3 of 3), go to **OPEN** again (the wait starts again from now). Otherwise go to **CLOSED**. |

### The 50 percent rule (`tooManyFailures`, already written)
| Failures | Calls | Too many? |
|---|---|---|
| 0 | 0 | no (nothing to judge) |
| 2 | 5 | no |
| 3 | 5 | yes |
| 1 | 3 | no |
| 2 | 3 | yes |

### Examples (with the data used by `TryIt.java`)
| What happens | The answer |
|---|---|
| a new breaker | state `CLOSED`, `allowCall()` is `true` |
| 4 failures in a row | still `CLOSED` (we judge only when there are 5 results) |
| a 5th result, a success (4 of 5 failed) | `OPEN` |
| `allowCall()` at 0 ms, and at 15000 ms | `false` |
| `allowCall()` at 15001 ms | `true`, and the state is `HALF_OPEN` |
| 4 calls before any result comes back | `true`, `true`, `true`, `false` |
| the 3 test results are success, failure, success (1 of 3 failed) | `CLOSED` |
| the 3 test results are failure, failure, success (2 of 3 failed) | `OPEN` again, and the 15 seconds start again |
| 3 successes, then 2 failures (2 of 5), then one more failure | `CLOSED`, then `OPEN` (the oldest success was dropped: the last 5 have 3 failures) |

### The rules
- **Only the last 5 results count in CLOSED.** The sixth result pushes the oldest one out.
- **No judging too early.** Four failures out of four calls do not open the breaker. We wait for 5 results.
- **A result that arrives while OPEN is ignored.** (A slow call can finish after the breaker has opened.)
- **Going to OPEN, HALF_OPEN or CLOSED always starts with an empty list of results.** The helper methods `toOpen`, `toHalfOpen` and `toClosed` already do this, so use them.
- **Use only `clock.getAsLong()` for the time.** Do not use `System.currentTimeMillis()` and do not sleep.

To achieve these requirements we need to do 2 things:
- **Task 1:** finish `allowCall()`: may this call go to the friend?
- **Task 2:** finish `recordResult(boolean failed)`: a call has finished, so how did it go?

## Instructions
1. Open `src/main/java/shopnest/breaker/CircuitBreaker.java`. It has two TODO notes: `TODO 1` in `allowCall` and `TODO 2` in `recordResult`. The notes tell you what to do in each state, in words.
2. Do not modify `State.java`, the names of the constants, the constructor, `state()`, `recordSuccess()`, `recordFailure()`, the names and parameters of the two methods, or the helper methods that are already written. Feel free to add new methods if required.
3. Run `TryIt` to see what your code does. From this folder (the one that contains `src/` and `pom.xml`), on Mac or Linux:
   ```
   javac -d out src/main/java/shopnest/breaker/*.java
   java -cp out shopnest.breaker.TryIt
   ```
   On Windows, write `src\main\java\shopnest\breaker\*.java` in the first command. In an IDE you can also press Run next to `main` in `TryIt.java`. When both tasks are finished, you should see exactly this:
   ```
   Scenario 1: a friend that gets well
     a new breaker, its state                     : CLOSED
     allowCall()                                  : true
     after 4 failures, the state                  : CLOSED
     the 5th call succeeds (4 of 5 failed)        : OPEN
     allowCall() at 0 ms                          : false
     allowCall() at 15000 ms (not MORE)           : false
     allowCall() at 15001 ms                      : true
     the state now                                : HALF_OPEN
     test call 2                                  : true
     test call 3                                  : true
     test call 4 (no result yet)                  : false
     after 2 results (success, failure)           : HALF_OPEN
     the 3rd result is a success (1 of 3 failed)  : CLOSED
     allowCall()                                  : true
   Scenario 2: a friend that is still sick
     after 5 failures, the state                  : OPEN
     allowCall() at 16001 ms                      : true
     test call 2                                  : true
     test call 3                                  : true
     the results: failure, failure, success       : OPEN
     allowCall() at 16001 ms                      : false
     allowCall() at 31001 ms (15000 later)        : false
     allowCall() at 31002 ms                      : true
   Scenario 3: only the last 5 calls count
     3 successes, then 2 failures (2 of 5)        : CLOSED
     one more failure (last 5: 3 failed)          : OPEN
   ```
   Before you start, `TryIt` already runs: `allowCall()` says `false` every time, and the state never leaves `CLOSED`.
4. Your code will be checked with the test file `src/test/java/shopnest/breaker/CircuitBreakerTest.java`. It checks the cases in the Requirements above, and the rules. While you write your code, use `TryIt` (instruction 3) to check your work.
5. You do not need to install anything, and you do not need a server, a database, the internet or a key. Only Java's standard library is used. It works with Java 11 or newer. (Maven is optional: `mvn compile` also works.)
6. Hints (all of these use ideas from class):
   - **Task 1 (`allowCall`).** First, if the breaker is OPEN and more than `WAIT_MILLIS` have passed since `openedAt`, call `toHalfOpen()`. After that, look at the state. A call may go through in CLOSED, and in HALF_OPEN while `testCallsGiven` is below `TEST_CALLS` (count each one you give). In every other case the answer is `false`.
     ```java
     if (state == State.OPEN && clock.getAsLong() - openedAt > WAIT_MILLIS) {
         toHalfOpen();
     }
     if (state == State.CLOSED) {
         return true;
     }
     if (state == State.HALF_OPEN && testCallsGiven < TEST_CALLS) {
         testCallsGiven++;
         return true;
     }
     return false;
     ```
   - **Task 2 (`recordResult`).** Do nothing in OPEN. In the other two states, add the result to `results`. In CLOSED, drop the oldest result when there are more than `WINDOW_SIZE`. Then work out how many results you need before you judge: `WINDOW_SIZE` in CLOSED, `TEST_CALLS` in HALF_OPEN. When you have exactly that many, ask `tooManyFailures(failureCount(), needed)`: if yes, `toOpen()`; if no and the state is HALF_OPEN, `toClosed()`.
     ```java
     if (state == State.OPEN) {
         return;
     }
     results.addLast(failed);
     if (state == State.CLOSED && results.size() > WINDOW_SIZE) {
         results.removeFirst();
     }
     int needed = (state == State.CLOSED) ? WINDOW_SIZE : TEST_CALLS;
     if (results.size() == needed) {
         if (tooManyFailures(failureCount(), needed)) {
             toOpen();
         } else if (state == State.HALF_OPEN) {
             toClosed();
         }
     }
     ```
   - **If something looks wrong.** If `after 4 failures` shows `OPEN`, you judged before there were 5 results. If `allowCall() at 15000 ms` shows `true`, use `>` and not `>=`. If `test call 4` shows `true`, you did not count the test calls you gave. If `after 2 results` shows `OPEN` or `CLOSED`, you judged before there were 3 results. If `the 3rd result is a success` shows `OPEN`, one failed test call is not enough: use `tooManyFailures`. If `allowCall() at 31001 ms` shows `true`, the 15 seconds did not start again: go to OPEN with `toOpen()`, which remembers the time. If `one more failure` shows `CLOSED`, you did not drop the oldest result.
