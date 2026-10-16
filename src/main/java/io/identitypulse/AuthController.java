package io.identitypulse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@RestController
public class AuthController {
 private final PolicyStore policy; private final ObservationRegistry observations;
 public AuthController(PolicyStore policy, ObservationRegistry observations) {
  this.policy=policy; this.observations=observations;
 }
 @PostMapping("/v1/auth/verify") public ResponseEntity<Map<String,Object>> verify(@AuthenticationPrincipal Jwt jwt) {
  return Observation.createNotStarted("identity.policy.check", observations).observe(() -> {
   try {
    if (!policy.allowed(jwt.getSubject(), jwt.getId())) return ResponseEntity.status(403).body(Map.of("error", "access_denied"));
    return ResponseEntity.ok(Map.of("authenticated", true));
   } catch (RuntimeException e) {
    // Do not expose dependency details, token claims, or credentials to clients/logs.
    return ResponseEntity.status(503).header("Retry-After", "5").body(Map.of("error", "authorization_unavailable"));
   }
  });
 }
 @GetMapping("/v1/admin/status") public Map<String,String> status() { return Map.of("service", "identitypulse"); }
}
