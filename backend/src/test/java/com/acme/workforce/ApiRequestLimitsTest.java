package com.acme.workforce;
import com.acme.workforce.common.infrastructure.ApiRequestLimits;
import com.acme.workforce.common.application.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ApiRequestLimitsTest {
 MockHttpServletRequest request(String body){var r=new MockHttpServletRequest("POST","/api/v1/invitations/preview");r.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;}
 @Test void boundedBodySurvivesForController()throws Exception{var r=new ApiRequestLimits(Clock.systemUTC(),16,2,10).protect(request("{\"token\":\"x\"}"),"a");assertThat(new String(r.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("{\"token\":\"x\"}");}
 @Test void declaredAndStreamedBodiesAreBounded(){var limits=new ApiRequestLimits(Clock.systemUTC(),16,2,10);assertThatThrownBy(()->limits.protect(request("x".repeat(17)),"a")).isInstanceOfSatisfying(DomainException.class,e->assertThat(e.status()).isEqualTo(413));
  var streamed=new MockHttpServletRequest("POST","/api/v1/projects"){@Override public long getContentLengthLong(){return -1;}};streamed.setContent(new byte[17]);assertThatThrownBy(()->limits.protect(streamed,"a")).isInstanceOfSatisfying(DomainException.class,e->assertThat(e.status()).isEqualTo(413));}
 @Test void principalBudgetCannotBeBypassedByOrganizationHeader()throws Exception{var limits=new ApiRequestLimits(Clock.systemUTC(),16,1,10);limits.protect(request("{}"),"a");var other=request("{}");other.addHeader("X-Organization-Id",UUID.randomUUID());assertThatThrownBy(()->limits.protect(other,"a")).isInstanceOfSatisfying(DomainException.class,e->assertThat(e.status()).isEqualTo(429));limits.protect(other,"b");}
 @Test void concurrentRequestsShareOneBoundedBudget()throws Exception{var limits=new ApiRequestLimits(Clock.systemUTC(),16,3,10);var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(8)){var tasks=new ArrayList<Future<Integer>>();for(int i=0;i<8;i++)tasks.add(pool.submit(()->{start.await();try{limits.protect(request("{}"),"a");return 200;}catch(DomainException e){return e.status();}}));start.countDown();var statuses=new ArrayList<Integer>();for(var task:tasks)statuses.add(task.get(5,TimeUnit.SECONDS));assertThat(statuses.stream().filter(s->s==200).count()).isEqualTo(3);assertThat(statuses.stream().filter(s->s==429).count()).isEqualTo(5);}}
 @Test void expiredWindowsReleaseSpaceWithoutUnboundedMemory()throws Exception{var now=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-16T00:00:00Z"));Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};var limits=new ApiRequestLimits(clock,16,1,1);limits.protect(request("{}"),"a");assertThatThrownBy(()->limits.protect(request("{}"),"b")).isInstanceOf(DomainException.class);now.set(now.get().plusSeconds(60));limits.protect(request("{}"),"b");}
 @Test void ordinaryReadsDoNotConsumeSensitiveBudget()throws Exception{var limits=new ApiRequestLimits(Clock.systemUTC(),16,1,1);for(int i=0;i<10;i++)limits.protect(new MockHttpServletRequest("GET","/api/v1/dashboard"),"a");limits.protect(request("{}"),"a");}
}
