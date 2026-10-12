package com.acme.workforce.common.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Stream;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

class ExceptionHandlerAdviceTest {
 static Stream<ConcurrencyFailureException> conflicts() {
  return Stream.of(new OptimisticLockingFailureException("internal version detail"),
   new CannotAcquireLockException("internal deadlock detail"),
   new CannotSerializeTransactionException("internal serialization detail"));
 }
 @ParameterizedTest @MethodSource("conflicts")
 void databaseConcurrencyFailuresRemainSafeConflicts(ConcurrencyFailureException failure) throws Exception {
  var mvc=MockMvcBuilders.standaloneSetup(new ConflictingController(failure))
   .setControllerAdvice(new ExceptionHandlerAdvice()).build();
  mvc.perform(post("/conflict")).andExpect(status().isConflict())
   .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
   .andExpect(jsonPath("$.path").value("/conflict"))
   .andExpect(content().string(not(containsString("internal"))));
 }
 @RestController static class ConflictingController {
  private final ConcurrencyFailureException failure;
  ConflictingController(ConcurrencyFailureException failure) { this.failure=failure; }
  @PostMapping("/conflict") void conflict() { throw failure; }
 }
}
