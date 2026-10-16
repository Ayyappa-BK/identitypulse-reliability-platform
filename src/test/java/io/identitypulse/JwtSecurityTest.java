package io.identitypulse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration", "management.tracing.sampling.probability=0", "management.endpoint.health.group.readiness.include=readinessState"})
@AutoConfigureMockMvc
class JwtSecurityTest {
 static volatile boolean issuerUnavailable=false;
 static final RSAKey KEY; static final HttpServer JWKS;
 static {
  try {
   KEY=new RSAKeyGenerator(2048).keyID("test-key").generate();
   JWKS=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
   JWKS.createContext("/jwks", x -> { if (issuerUnavailable) { x.sendResponseHeaders(503,-1);x.close();return; } byte[] b=new JWKSet(KEY.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type","application/json"); x.sendResponseHeaders(200,b.length); x.getResponseBody().write(b); x.close(); });
   JWKS.start();
  } catch(Exception e) { throw new ExceptionInInitializerError(e); }
 }
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
  r.add("identity.issuer", () -> "https://issuer.test");
  r.add("identity.jwks-uri", () -> "http://127.0.0.1:"+JWKS.getAddress().getPort()+"/jwks");
  r.add("spring.data.redis.host", () -> "localhost");
 }
 @AfterAll static void stop() { JWKS.stop(0); }
 @Autowired MockMvc mvc;
 @MockitoBean PolicyStore policy;
 String token(String issuer, String audience, Instant expiry, String scope, String subject, String jti, RSAKey key) throws Exception {
  var claims=new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(subject).jwtID(jti)
   .expirationTime(expiry==null?null:Date.from(expiry)).notBeforeTime(Date.from(Instant.now().minusSeconds(5)))
   .claim("scope",scope).build();
  var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),claims);
  jwt.sign(new RSASSASigner(key)); return jwt.serialize();
 }
 @Test void issuerFailureIs503RatherThanCredentialRejection() throws Exception {
  var key=new RSAKeyGenerator(2048).keyID("uncached-key").generate();
  var claims=new JWTClaimsSet.Builder().issuer("https://issuer.test").audience("identitypulse").subject("alice").jwtID("id-1").expirationTime(Date.from(Instant.now().plusSeconds(120))).claim("scope","identity:verify").build();
  var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("uncached-key").build(),claims); jwt.sign(new RSASSASigner(key));
  issuerUnavailable=true;
  try { mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer "+jwt.serialize())).andExpect(status().isServiceUnavailable()); }
  finally { issuerUnavailable=false; }
 }
 String good() throws Exception { return token("https://issuer.test","identitypulse",Instant.now().plusSeconds(120),"identity:verify","alice","id-1",KEY); }
 @Test void validSignedJwtAndPolicySucceed() throws Exception { when(policy.allowed("alice","id-1")).thenReturn(true); mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer "+good())).andExpect(status().isOk()); }
 @Test void missingTokenIs401() throws Exception { mvc.perform(post("/v1/auth/verify")).andExpect(status().isUnauthorized()); verifyNoInteractions(policy); }
 @Test void malformedIs401() throws Exception { mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer broken")).andExpect(status().isUnauthorized()); }
 @Test void deniesWrongClaimsAndSignature() throws Exception {
  String[][] invalid={{"https://evil.test","identitypulse","alice","id-1"},{"https://issuer.test","wrong","alice","id-1"},{"https://issuer.test","identitypulse",null,"id-1"},{"https://issuer.test","identitypulse","alice",null}};
  for(var c:invalid) mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer "+token(c[0],c[1],Instant.now().plusSeconds(120),"identity:verify",c[2],c[3],KEY))).andExpect(status().isUnauthorized());
  mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer "+token("https://issuer.test","identitypulse",Instant.now().plusSeconds(120),"identity:verify","alice","id-1",new RSAKeyGenerator(2048).generate()))).andExpect(status().isUnauthorized());
  verifyNoInteractions(policy);
 }
 @Test void expiredOrMissingExpiryDenied() throws Exception {
  for(Instant exp: new Instant[]{Instant.now().minusSeconds(120),null}) mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer "+token("https://issuer.test","identitypulse",exp,"identity:verify","alice","id-1",KEY))).andExpect(status().isUnauthorized());
 }
 @Test void noScopeDenied() throws Exception { mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer "+token("https://issuer.test","identitypulse",Instant.now().plusSeconds(120),"","alice","id-1",KEY))).andExpect(status().isForbidden()); verifyNoInteractions(policy); }
 @Test void adminRequiresSeparateScope() throws Exception { mvc.perform(get("/v1/admin/status").header("Authorization","Bearer "+good())).andExpect(status().isForbidden()); }
 @Test void revokedOrDisabledDenied() throws Exception { when(policy.allowed(anyString(),anyString())).thenReturn(false); mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer "+good())).andExpect(status().isForbidden()); }
 @Test void dependencyOutageFailsClosedThenRecovers() throws Exception {
  when(policy.allowed(anyString(),anyString())).thenThrow(new IllegalStateException("db down")).thenReturn(true);
  mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer "+good())).andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","5"));
  mvc.perform(post("/v1/auth/verify").header("Authorization","Bearer "+good())).andExpect(status().isOk());
 }
 @Test void unknownEndpointDenied() throws Exception { mvc.perform(get("/anything").header("Authorization","Bearer "+good())).andExpect(status().isForbidden()); }
}
