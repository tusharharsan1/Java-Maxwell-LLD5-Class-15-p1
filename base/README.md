# The Circuit Breaker at ShopNest

## Problem Statement
ShopNest shows a delivery estimate on every product page. The number of days comes from a courier service, which is a "friend" that our shop calls. One day the courier is down. Every request now waits, fails, and keeps a cashier (a server thread) busy for nothing. In class, you put a **circuit breaker** on the Groq call, and you gave the customer a simple answer when Groq failed. Now you do the same for the courier, in a small Spring Boot application: the settings of the breaker, one annotation, the simple answer, and the endpoint. You need about 15 lines of code, in 4 small tasks. There is no database and no internet: the courier is pretend, and you can switch it off with one URL.

## Requirements
### What the application does
| Request | Answer | Written already? |
|---|---|---|
| `GET /delivery/{pincode}` | the delivery estimate, as JSON | **no** (Task 4) |
| `GET /pretend/courier?down=true` (or `false`) | switches the pretend courier off (or on) | yes |
| `GET /actuator/circuitbreakers` | lets you look inside the breaker | yes |

The estimate has three fields: `pincode`, `days` and `source`.

| When | `days` | `source` |
|---|---|---|
| the courier answers | the days the courier gave (the pretend courier says 3) | `"courier"` |
| the courier fails, **or the breaker is OPEN and refuses the call** | 5 | `"default"` |

The customer must **never** see an error. A failing courier is answered with the simple answer: 5 days.

### The breaker called `courier`
It protects the call to the courier. It has these settings (put them in `application.properties`):

| Setting | Value | What it means |
|---|---|---|
| `sliding-window-size` | 4 | look at the last 4 calls |
| `minimum-number-of-calls` | 4 | do not judge before 4 calls have been made |
| `failure-rate-threshold` | 50 | 50 percent or more failing is "too many" |
| `wait-duration-in-open-state` | 10s | stay OPEN for 10 seconds |
| `permitted-number-of-calls-in-half-open-state` | 2 | in HALF_OPEN, let 2 test calls through |

### The three states with these numbers
| State | What happens |
|---|---|
| **CLOSED** | Calls go to the courier. When 2 or more of the last 4 calls failed (50 percent or more), the breaker goes **OPEN**. |
| **OPEN** | Calls are **refused at once**, and the courier is not called. The customer gets the simple answer. After 10 seconds, the next call moves the breaker to **HALF_OPEN**. |
| **HALF_OPEN** | 2 test calls go to the courier. If 50 percent or more of them fail (1 of 2 is enough), the breaker goes **OPEN** again. If both work, it goes back to **CLOSED**. |

### Examples (with the steps used below)
| What happens | The answer |
|---|---|
| the courier is healthy | `{"pincode":"400001","days":3,"source":"courier"}` |
| the courier is down | `{"pincode":"400001","days":5,"source":"default"}` |
| the courier is down for 4 calls in a row | the breaker goes OPEN; call 5 and the next ones never reach the courier, but they still get `days` 5 |
| 10 seconds later, the courier is well again | the next 2 calls go through (`"courier"`), and the breaker is CLOSED |
| 10 seconds later, test call 1 fails and test call 2 works | 1 of 2 failed, so the breaker is OPEN again |

### The rules
- **The breaker belongs on `CourierClient`**, the one class that talks to the courier. Do not put it on the controller or on the service.
- **The simple answer belongs in `DeliveryService`.** When the breaker is OPEN it throws an exception, and that is also a failure of the call: the service must answer with the simple answer for that too.
- **Name the breaker `courier`** (the settings and the annotation must use the same name).
- **An OPEN breaker must not call the courier.** The pretend courier writes one line in the console for every call it gets, so you can count the calls that really reach it.

To achieve these requirements we need to do 4 things:
- **Task 1:** write the settings of the breaker in `application.properties`.
- **Task 2:** put the circuit breaker on `CourierClient.estimateDays`.
- **Task 3:** finish `DeliveryService.estimate`: the simple answer when the call fails.
- **Task 4:** add the endpoint `GET /delivery/{pincode}` to `DeliveryController`.

## Instructions
1. There is one TODO note in each of these four files: `application.properties` (`TODO 1`), `CourierClient.java` (`TODO 2`), `DeliveryService.java` (`TODO 3`) and `DeliveryController.java` (`TODO 4`).
2. Do not modify the other files (they are finished), the dependencies in `pom.xml` (they are the same three as in class: the actuator, AspectJ and Resilience4j), the names of the classes and methods, or the dependencies that are injected in the classes. Feel free to add new methods or imports if required.
3. Start the application (`mvn spring-boot:run`, or run `DeliveryApplication` in your IDE). It runs on port 8080. Then try these commands in a second terminal, and compare with the answers below. When all four tasks are finished, you should see exactly this:
   ```
   $ curl localhost:8080/delivery/400001
   {"pincode":"400001","days":3,"source":"courier"}

   $ curl "localhost:8080/pretend/courier?down=true"
   courier is now: down=true

   $ curl localhost:8080/delivery/400001        <- send this 6 times; every answer is the same:
   {"pincode":"400001","days":5,"source":"default"}

   $ curl localhost:8080/actuator/circuitbreakers
   {"circuitBreakers":{"courier":{"bufferedCalls":4,"failedCalls":3,"failureRate":"75.0%","failureRateThreshold":"50.0%","notPermittedCalls":3,"slowCallRate":"0.0%","slowCallRateThreshold":"100.0%","slowCalls":0,"slowFailedCalls":0,"state":"OPEN"}}}
   ```
   Look at the console of your application too. The pretend courier wrote **4 lines** (`pretend courier: 400001 -> 3 days` once, and `... -> I am down` three times), not 7: calls 4, 5 and 6 of the second part never left your shop. (The first healthy call counts in the window too: 1 success and 3 failures are 4 calls, and 75 percent of them failed.)

   Now let the courier get well. Wait 10 seconds after the breaker went OPEN, then:
   ```
   $ curl "localhost:8080/pretend/courier?down=false"
   courier is now: down=false

   $ curl localhost:8080/delivery/400001        <- send this 2 times; every answer is the same:
   {"pincode":"400001","days":3,"source":"courier"}
   ```
   After these two calls, `curl localhost:8080/actuator/circuitbreakers` shows `"state":"CLOSED"`.

   Before you start, the application already runs: `curl localhost:8080/delivery/400001` gives an empty `404` (there is no endpoint yet), and `curl localhost:8080/actuator/circuitbreakers` gives `{"circuitBreakers":{}}`.
4. Your code will be checked with the test file `src/test/java/com/shopnest/delivery/DeliveryBreakerTest.java`. It checks the cases in the Requirements above, and the rules. While you write your code, use the commands in instruction 3 to check your work.
5. You do not need a database, the internet or a key. It works with Java 17 and Maven, like the project from class.
6. Hints (all of these use ideas from class):
   - **Task 1 (settings).** Every line starts with `resilience4j.circuitbreaker.instances.courier.` and then the name of the setting. The first line is:
     ```properties
     resilience4j.circuitbreaker.instances.courier.sliding-window-size=4
     ```
     The names of the other four settings are in the table of the Requirements (`minimum-number-of-calls`, `failure-rate-threshold`, `wait-duration-in-open-state` and `permitted-number-of-calls-in-half-open-state`). Write a number for the first two, a number for the third, and a number with `s` (for seconds) for the fourth.
   - **Task 2 (the annotation).** One line above the method. The import is `io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker`:
     ```java
     @CircuitBreaker(name = "courier")
     public int estimateDays(String pincode) {
     ```
   - **Task 3 (the simple answer).** Put the line that is already there inside a `try`, and answer with the simple answer in a `catch`. Catch `RuntimeException`: it covers a courier that is down and a breaker that refuses (both throw a `RuntimeException`).
     ```java
     try {
         return new DeliveryEstimate(pincode, courierClient.estimateDays(pincode), "courier");
     } catch (RuntimeException e) {
         return new DeliveryEstimate(pincode, DEFAULT_DAYS, "default");
     }
     ```
   - **Task 4 (the endpoint).** The class already has `@RequestMapping("/delivery")`. Do not repeat `/delivery` in the method. `{pincode}` is a part of the path:
     ```java
     @GetMapping("/{pincode}")
     public DeliveryEstimate estimate(@PathVariable String pincode) {
         return deliveryService.estimate(pincode);
     }
     ```
   - **If something looks wrong.** If the delivery endpoint gives `404`, Task 4 is not done, or the path is wrong. If the console shows 7 lines and not 4, the breaker does not work: check the annotation (Task 2), the name `courier` in both places, and the property names (Task 1). If `curl localhost:8080/actuator/circuitbreakers` shows `{"circuitBreakers":{}}` after some calls, the annotation is missing. If the customer gets a `500` error when the courier is down, the simple answer is missing. If calls 1 to 4 are fine but calls 5 and 6 give `500`, you catch only the courier's own exception: a breaker that refuses a call throws a different exception, so catch `RuntimeException`. If the application does not start, look for a typo in a property name.
