package io.identitypulse;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Tag("integration") @Testcontainers
class DependenciesIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
 @Test void realDependenciesRevocationOutageAndRecovery() throws Exception {
  var db=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl()+"?socketTimeout=1",postgres.getUsername(),postgres.getPassword()));
  db.execute("CREATE TABLE principals(subject varchar(200) primary key, enabled boolean not null default false)");
  db.update("INSERT INTO principals VALUES (?,true)","alice");
  var cfg=new RedisStandaloneConfiguration(redis.getHost(),redis.getMappedPort(6379));
  var client=LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).shutdownTimeout(Duration.ofMillis(100)).build();
  var cf=new LettuceConnectionFactory(cfg,client); cf.afterPropertiesSet(); cf.start();
  try {
   var template=new StringRedisTemplate(cf); var policy=new PolicyStore(db,template);
   assertTrue(policy.allowed("alice","t1")); assertFalse(policy.allowed("unknown","t1"));
   template.opsForValue().set("revoked:t1","1",Duration.ofSeconds(120)); assertFalse(policy.allowed("alice","t1"));
   template.delete("revoked:t1"); assertTrue(policy.allowed("alice","t1"));
   redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
   try { assertThrows(RuntimeException.class,()->policy.allowed("alice","t1")); }
   finally { redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec(); }
   Thread.sleep(500); policy.breaker.reset(); assertTrue(policy.allowed("alice","t1"));
   postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
   try { assertThrows(RuntimeException.class,()->policy.allowed("alice","t1")); }
   finally { postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec(); }
   Thread.sleep(500); policy.breaker.reset(); assertTrue(policy.allowed("alice","t1"));
  } finally { cf.destroy(); }
 }
}
