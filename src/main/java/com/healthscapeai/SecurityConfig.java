package com.healthscapeai;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain chain(HttpSecurity h) throws Exception {
        h.authorizeHttpRequests(a -> a
            .requestMatchers("/admin/**", "/official/alerts/*/assign").hasRole("ADMIN")
            .requestMatchers("/official/**").hasAnyRole("ADMIN", "WORKER")
            .anyRequest().permitAll())
         .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/official", true).permitAll())
         .logout(l -> l.logoutSuccessUrl("/"))
         .headers(headers -> headers
             .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
             .frameOptions(frame -> frame.deny())
             .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
         .csrf(c -> c.ignoringRequestMatchers("/api/predict", "/api/environment/ai-brief"));
        return h.build();
    }
    @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(AppUserRepository r) {
        return n -> r.findByUsername(n)
                .map(u -> User.withUsername(u.username).password(u.password).roles(u.role == null ? "ADMIN" : u.role).build())
                .orElseThrow(() -> new UsernameNotFoundException(n));
    }
}
