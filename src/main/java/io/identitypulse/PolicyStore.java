package io.identitypulse;
import java.time.Duration;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Critical authorization data is never served from a stale or permissive fallback. */
@Service
public class PolicyStore {
 private final JdbcTemplate db; private final StringRedisTemplate redis;
 final CircuitBreaker breaker = CircuitBreaker.of("policy", CircuitBreakerConfig.custom()
   .slidingWindowSize(20).minimumNumberOfCalls(5).failureRateThreshold(50)
   .waitDurationInOpenState(Duration.ofSeconds(5)).permittedNumberOfCallsInHalfOpenState(2).build());
 private final Retry retry = Retry.of("policy-read", RetryConfig.custom().maxAttempts(2)
   .waitDuration(Duration.ofMillis(25)).retryExceptions(TransientDataAccessException.class).build());
 public PolicyStore(JdbcTemplate db, StringRedisTemplate redis) { this.db=db; this.redis=redis; }
 public boolean allowed(String subject, String jti) {
  // One retry only for idempotent transient reads; never retry invalid credentials.
  return CircuitBreaker.decorateSupplier(breaker, Retry.decorateSupplier(retry, () -> {
   Boolean revoked = redis.hasKey("revoked:" + jti);
   if (revoked == null) throw new IllegalStateException("Revocation check unavailable");
   if (revoked) return false;
   var active = db.query("select enabled from principals where subject = ?",
     (rs, row) -> rs.getBoolean(1), subject);
   return active.size() == 1 && active.getFirst();
  })).get();
 }
}
