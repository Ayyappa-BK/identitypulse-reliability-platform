package io.identitypulse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthMetricsFilter extends org.springframework.web.filter.OncePerRequestFilter {
 private final MeterRegistry registry;
 public AuthMetricsFilter(MeterRegistry registry) { this.registry=registry;
  for (String outcome : new String[]{"success","error","denied","unauthenticated","other"}) registry.counter("identity.auth.requests", "outcome", outcome); }
 @Override protected boolean shouldNotFilter(HttpServletRequest req) { return !req.getRequestURI().equals("/v1/auth/verify"); }
 @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
   throws ServletException, IOException {
  var sample = Timer.start(registry);
  try { chain.doFilter(req, res); }
  finally {
   int code=res.getStatus();
   String outcome = code >= 500 ? "error" : code == 401 ? "unauthenticated" : code == 403 ? "denied" : code < 300 ? "success" : "other";
   registry.counter("identity.auth.requests", "outcome", outcome).increment();
   sample.stop(Timer.builder("identity.auth.duration").tag("outcome", outcome)
     .publishPercentileHistogram().serviceLevelObjectives(java.time.Duration.ofMillis(300)).register(registry));
  }
 }
}
