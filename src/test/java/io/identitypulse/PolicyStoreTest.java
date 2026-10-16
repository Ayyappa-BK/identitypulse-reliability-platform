package io.identitypulse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.TransientDataAccessResourceException;
import java.util.List;
class PolicyStoreTest {
 JdbcTemplate db; StringRedisTemplate redis; PolicyStore policy;
 @BeforeEach void setup() { db=mock(JdbcTemplate.class); redis=mock(StringRedisTemplate.class); policy=new PolicyStore(db,redis); }
 @Test void revokedFailsBeforeDatabase() { when(redis.hasKey("revoked:token")).thenReturn(true); assertFalse(policy.allowed("alice","token")); verifyNoInteractions(db); }
 @Test void missingSubjectDenied() { when(redis.hasKey(anyString())).thenReturn(false); when(db.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Boolean>>any(),eq("alice"))).thenReturn(List.of()); assertFalse(policy.allowed("alice","token")); }
 @Test void nullRedisResponseFailsClosed() { when(redis.hasKey(anyString())).thenReturn(null); assertThrows(IllegalStateException.class,()->policy.allowed("alice","token")); }
 @Test void transientReadsHaveExactlyTwoAttempts() {
  when(redis.hasKey(anyString())).thenThrow(new TransientDataAccessResourceException("outage"));
  assertThrows(TransientDataAccessResourceException.class,()->policy.allowed("alice","token")); verify(redis,times(2)).hasKey(anyString());
 }
 @Test void breakerOpensAndRecovers() {
  when(redis.hasKey(anyString())).thenThrow(new IllegalStateException("unavailable"));
  for(int i=0;i<5;i++) assertThrows(IllegalStateException.class,()->policy.allowed("alice","token"));
  assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN,policy.breaker.getState());
  assertThrows(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,()->policy.allowed("alice","token"));
  policy.breaker.transitionToHalfOpenState();
  doReturn(true).when(redis).hasKey(anyString());
  assertFalse(policy.allowed("alice","token")); assertFalse(policy.allowed("alice","token"));
  assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED,policy.breaker.getState());
 }
}
