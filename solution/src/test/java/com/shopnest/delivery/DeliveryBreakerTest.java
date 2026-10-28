package com.shopnest.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Checks the circuit breaker problem of Class 15 (The Circuit Breaker).
 * The courier is replaced by a mock, so every test decides exactly what the courier does.
 * "S" in a sequence means the courier answers (3 days), "F" means the courier is down.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DeliveryBreakerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockitoBean
    private PretendCourier courier;

    @BeforeEach
    void startWithAFreshBreaker() {
        breaker().reset();
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
        OngoingStubbing<Integer> stub = null;
        for (char step : sequence.toCharArray()) {
            if (stub == null) {
                stub = step == 'S'
                        ? when(courier.daysTo(anyString())).thenReturn(3)
                        : when(courier.daysTo(anyString())).thenThrow(new CourierDownException("down"));
            } else {
                stub = step == 'S' ? stub.thenReturn(3) : stub.thenThrow(new CourierDownException("down"));
            }
        }
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
        when(courier.daysTo("560001")).thenReturn(2);

        ask("560001")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pincode").value("560001"))
                .andExpect(jsonPath("$.days").value(2))
                .andExpect(jsonPath("$.source").value("courier"));
    }

    @Test
    @DisplayName("Task 4: another pincode and another answer (no fixed answers)")
    void endpointUsesThePincodeAndTheDays() throws Exception {
        when(courier.daysTo("110011")).thenReturn(7);

        ask("110011")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pincode").value("110011"))
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.source").value("courier"));
        verify(courier).daysTo("110011");
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

    @ParameterizedTest(name = "Task 3: any failure of the courier is covered ({0})")
    @ValueSource(classes = {IllegalStateException.class, NullPointerException.class, RuntimeException.class})
    void anyFailureGivesTheSimpleAnswer(Class<? extends RuntimeException> failure) throws Exception {
        when(courier.daysTo(anyString())).thenThrow(failure);

        ask("560001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.source").value("default"));
    }

    @Test
    @DisplayName("Task 3: a healthy courier is never replaced by the simple answer")
    void aHealthyCourierIsNotReplaced() throws Exception {
        when(courier.daysTo(anyString())).thenReturn(9);

        ask("400001")
                .andExpect(jsonPath("$.days").value(9))
                .andExpect(jsonPath("$.source").value("courier"));
    }

    @Test
    @DisplayName("Task 3: when the breaker is OPEN the customer still gets the simple answer, and the courier is not called")
    void openBreakerStillAnswers() throws Exception {
        breaker().transitionToOpenState();
        when(courier.daysTo(anyString())).thenReturn(3);

        ask("400001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.source").value("default"));
        verify(courier, never()).daysTo(anyString());
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
        verify(courier, times(4)).daysTo(anyString());
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
        verify(courier, times(4)).daysTo(anyString());
        askTimes(1);
        verify(courier, times(5)).daysTo(anyString());
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
        verify(courier, times(9)).daysTo(anyString());
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
