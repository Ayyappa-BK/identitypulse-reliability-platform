package io.identitypulse;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@ManagementContextConfiguration(proxyBeanMethods=false)
public class ManagementSecurity {
 @Bean @Order(0) SecurityFilterChain managementFilterChain(HttpSecurity http) throws Exception {
  return http.securityMatcher("/actuator/**").authorizeHttpRequests(a -> a
   .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness", "/actuator/prometheus").permitAll()
   .anyRequest().denyAll()).build();
 }
}
