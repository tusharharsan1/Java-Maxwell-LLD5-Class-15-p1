package com.shopnest.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checks the circuit breaker problem of Class 15 (The Circuit Breaker).
 * The courier is replaced by a small fake class written in this file (ScriptedCourier), so every test decides
 * exactly what the courier does, and counts the calls that really reach it. There is no Mockito here: only JUnit.
 * "S" in a sequence means the courier answers (3 days), "F" means the courier is down.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DeliveryBreakerTest {

    /**
     * The courier of these tests. It does what the test tells it, and it remembers every call that reaches it.
     * Call 1 gets step 1, call 2 gets step 2 ...; the last step repeats.
     * A step is a number (the courier answers with that many days) or an exception (the courier fails).
     */
    static class ScriptedCourier implements CourierApi {

        private final List<Object> steps = new ArrayList<>();
        private final List<String> pincodes = new ArrayList<>();

        /** Back to the start: a healthy courier (3 days), and no calls counted. */
        void reset() {
            steps.clear();
            steps.add(3);
            pincodes.clear();
        }

        void does(Object... newSteps) {
            steps.clear();
            steps.addAll(List.of(newSteps));
        }

        /** The pincode of every call that reached the courier, in order. */
        List<String> calls() {
            return pincodes;
        }

        @Override
        public int daysTo(String pincode) {
            Object step = steps.get(Math.min(pincodes.size(), steps.size() - 1));
            pincodes.add(pincode);
            if (step instanceof RuntimeException failure) {
                throw failure;
            }
            return (Integer) step;
        }
    }

    /** Puts the fake courier in the place of the pretend courier (@Primary wins when two beans fit CourierApi). */
    @TestConfiguration
    static class FakeCourierConfig {

        @Bean
        @Primary
        ScriptedCourier scriptedCourier() {
            return new ScriptedCourier();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CircuitBreakerRegistry registry;

    @Autowired
    private ScriptedCourier courier;

    @BeforeEach
    void startWithAFreshBreaker() {
        breaker().reset();
        courier.reset();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private io.github.resilience4j.circuitbreaker.CircuitBreaker breaker() {
        return registry.circuitBreaker("courier");
    }

    private ResultActions ask(String pincode) throws Exception {
        return mvc.perform(get("/delivery/" + pincode));
    }

    private void askTimes(int times) throws Exception {
        for (int i = 0; i < times; i++) {
            ask("400001").andReturn();
        }
    }

    /** What the courier does on call 1, 2, 3 ... ; the last step repeats. */
    private void courierDoes(String sequence) {
        Object[] steps = new Object[sequence.length()];
        for (int i = 0; i < steps.length; i++) {
            if (sequence.charAt(i) == 'S') {
                steps[i] = 3;
            } else {
                steps[i] = new CourierDownException("down");
            }
        }
        courier.does(steps);
    }

    /** Sends one request per letter of the sequence, with the courier doing exactly that. */
    private void runSequence(String sequence) throws Exception {
        courierDoes(sequence);
        askTimes(sequence.length());
    }

    // ------------------------------------------------------------------
    // Task 4: the endpoint
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Task 4: GET /delivery/{pincode} gives the courier's answer as JSON")
    void endpointGivesTheCouriersAnswer() throws Exception {
        courier.does(2);

        ask("560001")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pincode").value("560001"))
                .andExpect(jsonPath("$.days").value(2))
                .andExpect(jsonPath("$.source").value("courier"));
        assertThat(courier.calls()).as("the pincode that reached the courier").containsExactly("560001");
    }

    @Test
    @DisplayName("Task 4: another pincode and another answer (no fixed answers)")
    void endpointUsesThePincodeAndTheDays() throws Exception {
        courier.does(7);

        ask("110011")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pincode").value("110011"))
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.source").value("courier"));
        assertThat(courier.calls()).as("the pincode that reached the courier").containsExactly("110011");
    }

    // ------------------------------------------------------------------
    // Task 3: the simple answer
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Task 3: a courier that is down gives the simple answer: 5 days, source default")
    void courierDownGivesTheSimpleAnswer() throws Exception {
        courierDoes("F");

        ask("560001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pincode").value("560001"))
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.source").value("default"));
    }

    static Stream<RuntimeException> failures() {
        return Stream.of(new IllegalStateException("boom"), new NullPointerException("boom"), new RuntimeException("boom"));
    }

    @ParameterizedTest(name = "Task 3: any failure of the courier is covered ({0})")
    @MethodSource("failures")
    void anyFailureGivesTheSimpleAnswer(RuntimeException failure) throws Exception {
        courier.does(failure);

        ask("560001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.source").value("default"));
    }

    @Test
    @DisplayName("Task 3: a healthy courier is never replaced by the simple answer")
    void aHealthyCourierIsNotReplaced() throws Exception {
        courier.does(9);

        ask("400001")
                .andExpect(jsonPath("$.days").value(9))
                .andExpect(jsonPath("$.source").value("courier"));
    }

    @Test
    @DisplayName("Task 3: when the breaker is OPEN the customer still gets the simple answer, and the courier is not called")
    void openBreakerStillAnswers() throws Exception {
        breaker().transitionToOpenState();
        courier.does(3);

        ask("400001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.source").value("default"));
        assertThat(courier.calls()).as("calls that reached the courier").isEmpty();
    }

    // ------------------------------------------------------------------
    // Task 2: the annotation, in the right place
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Task 2: CourierClient.estimateDays has @CircuitBreaker(name = \"courier\")")
    void theBreakerIsOnTheCourierClient() throws Exception {
        Method method = CourierClient.class.getMethod("estimateDays", String.class);
        CircuitBreaker annotation = AnnotatedElementUtils.findMergedAnnotation(method, CircuitBreaker.class);

        assertThat(annotation).as("@CircuitBreaker on CourierClient.estimateDays").isNotNull();
        assertThat(annotation.name()).isEqualTo("courier");
    }

    @Test
    @DisplayName("Task 2: the breaker is not on the service or on the controller")
    void theBreakerIsNotOnTheServiceOrTheController() {
        for (Class<?> type : new Class<?>[] {DeliveryService.class, DeliveryController.class}) {
            assertThat(AnnotatedElementUtils.findMergedAnnotation(type, CircuitBreaker.class))
                    .as("@CircuitBreaker on the class " + type.getSimpleName()).isNull();
            for (Method method : type.getDeclaredMethods()) {
                assertThat(AnnotatedElementUtils.findMergedAnnotation(method, CircuitBreaker.class))
                        .as("@CircuitBreaker on " + type.getSimpleName() + "." + method.getName()).isNull();
            }
        }
    }

    @Test
    @DisplayName("Task 2: after 4 failed calls the breaker is OPEN, and calls 5 and 6 never reach the courier")
    void breakerOpensAndStopsCallingTheCourier() throws Exception {
        courierDoes("F");

        askTimes(6);

        assertThat(breaker().getState()).isEqualTo(State.OPEN);
        assertThat(courier.calls()).as("calls that reached the courier").hasSize(4);
    }

    @Test
    @DisplayName("Task 2: the customer sees the simple answer for all 6 calls")
    void everyCallGetsAnAnswer() throws Exception {
        courierDoes("F");

        for (int i = 0; i < 6; i++) {
            ask("400001")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.days").value(5))
                    .andExpect(jsonPath("$.source").value("default"));
        }
    }

    // ------------------------------------------------------------------
    // Task 1 (and 2): the settings, seen through the behaviour
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Task 1: the breaker called courier has the five settings from the README")
    void theFiveSettings() {
        var config = breaker().getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowType()).as("the window counts calls, not seconds").isEqualTo(
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(config.getSlidingWindowSize()).as("sliding-window-size").isEqualTo(4);
        assertThat(config.getMinimumNumberOfCalls()).as("minimum-number-of-calls").isEqualTo(4);
        assertThat(config.getFailureRateThreshold()).as("failure-rate-threshold").isEqualTo(50.0f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1))
                .as("wait-duration-in-open-state, in milliseconds").isEqualTo(10_000L);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState())
                .as("permitted-number-of-calls-in-half-open-state").isEqualTo(2);
    }

    @Test
    @DisplayName("Task 1: three failures in a row are not enough (no judging before 4 calls)")
    void noJudgingBeforeFourCalls() throws Exception {
        runSequence("FFF");

        assertThat(breaker().getState()).isEqualTo(State.CLOSED);
    }

    @Test
    @DisplayName("Task 1: 1 success and 3 failures (75 percent) open the breaker")
    void threeFailuresOutOfFourOpen() throws Exception {
        runSequence("SFFF");

        assertThat(breaker().getState()).isEqualTo(State.OPEN);
    }

    @Test
    @DisplayName("Task 1: 2 failures out of 4 (exactly 50 percent) open the breaker")
    void twoFailuresOutOfFourOpen() throws Exception {
        runSequence("SSFF");

        assertThat(breaker().getState()).isEqualTo(State.OPEN);
    }

    @Test
    @DisplayName("Task 1: 1 failure out of 4 (25 percent) leaves the breaker CLOSED, and the next call still reaches the courier")
    void oneFailureOutOfFourStaysClosed() throws Exception {
        runSequence("SSSF");

        assertThat(breaker().getState()).isEqualTo(State.CLOSED);
        assertThat(courier.calls()).as("calls that reached the courier").hasSize(4);
        askTimes(1);
        assertThat(courier.calls()).as("calls that reached the courier").hasSize(5);
    }

    @Test
    @DisplayName("Task 1: only the last 4 calls count (the oldest result is dropped)")
    void onlyTheLastFourCallsCount() throws Exception {
        // calls 2 to 5 are S S F F: 50 percent. Calls 1 to 4 (S S S F) were only 25 percent.
        runSequence("SSSFF");

        assertThat(breaker().getState()).isEqualTo(State.OPEN);
    }

    @Test
    @DisplayName("Task 1: a failure now and then (never 2 in the last 4 calls) does not open the breaker")
    void aFailureNowAndThenDoesNotOpen() throws Exception {
        runSequence("FSSSFSSSF");

        assertThat(breaker().getState()).isEqualTo(State.CLOSED);
        assertThat(courier.calls()).as("calls that reached the courier").hasSize(9);
    }

    // ------------------------------------------------------------------
    // HALF_OPEN: 2 test calls, judged with the same 50 percent rule
    // ------------------------------------------------------------------

    private void goToHalfOpen() {
        breaker().transitionToOpenState();
        breaker().transitionToHalfOpenState();
    }

    @Test
    @DisplayName("HALF_OPEN: 2 good test calls close the breaker")
    void twoGoodTestCallsClose() throws Exception {
        goToHalfOpen();
        courierDoes("SS");

        ask("400001").andExpect(jsonPath("$.source").value("courier"));
        assertThat(breaker().getState()).as("after test call 1").isEqualTo(State.HALF_OPEN);
        ask("400001").andExpect(jsonPath("$.source").value("courier"));

        assertThat(breaker().getState()).isEqualTo(State.CLOSED);
    }

    @Test
    @DisplayName("HALF_OPEN: 1 failed test call out of 2 (50 percent) opens the breaker again")
    void oneFailedTestCallOutOfTwoReopens() throws Exception {
        goToHalfOpen();
        courierDoes("FS");

        ask("400001").andExpect(jsonPath("$.source").value("default"));
        assertThat(breaker().getState()).as("after test call 1").isEqualTo(State.HALF_OPEN);
        ask("400001").andExpect(jsonPath("$.source").value("courier"));

        assertThat(breaker().getState()).isEqualTo(State.OPEN);
    }

    @Test
    @DisplayName("HALF_OPEN: 2 failed test calls open the breaker again")
    void twoFailedTestCallsReopen() throws Exception {
        goToHalfOpen();
        courierDoes("FF");

        askTimes(2);

        assertThat(breaker().getState()).isEqualTo(State.OPEN);
    }
}
