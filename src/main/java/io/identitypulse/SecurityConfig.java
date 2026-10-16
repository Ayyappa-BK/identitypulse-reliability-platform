package io.identitypulse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class SecurityConfig {
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  org.springframework.security.web.AuthenticationEntryPoint errors = (req, res, failure) -> {
   if (failure instanceof org.springframework.security.authentication.AuthenticationServiceException) {
    res.setStatus(503); res.setHeader("Retry-After", "5");
   } else {
    new org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint().commence(req, res, failure);
   }
  };
  return http.csrf(c -> c.disable())
   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a -> a
    .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
    .requestMatchers(HttpMethod.POST, "/v1/auth/verify").hasAuthority("SCOPE_identity:verify")
    .requestMatchers(HttpMethod.GET, "/v1/admin/status").hasAuthority("SCOPE_identity:admin")
    .anyRequest().denyAll())
   .oauth2ResourceServer(o -> o.jwt(c -> {}).authenticationEntryPoint(errors)
    .withObjectPostProcessor(new org.springframework.security.config.ObjectPostProcessor<org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter>() {
     @Override public <O extends org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter> O postProcess(O filter) {
      var handler = new org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler(errors);
      handler.setRethrowAuthenticationServiceException(false);
      filter.setAuthenticationFailureHandler(handler);
      return filter;
     }
    })).build();
 }
 @Bean JwtDecoder jwtDecoder(@Value("${identity.issuer}") String issuer,
   @Value("${identity.jwks-uri}") String jwks, @Value("${identity.audience}") String audience) {
  var factory = new SimpleClientHttpRequestFactory();
  factory.setConnectTimeout(Duration.ofMillis(300)); factory.setReadTimeout(Duration.ofMillis(500));
  var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.RS256)
    .restOperations(new RestTemplate(factory)).build();
  decoder.setJwtValidator(validators(issuer, audience)); return decoder;
 }
 static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
  OAuth2TokenValidator<Jwt> required = jwt -> {
   boolean valid = jwt.getAudience().contains(audience) && jwt.getExpiresAt() != null
    && jwt.getSubject() != null && !jwt.getSubject().isBlank()
    && jwt.getId() != null && !jwt.getId().isBlank();
   return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
     new OAuth2Error("invalid_token", "Required claims are missing or invalid", null));
  };
  return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), required);
 }
}
