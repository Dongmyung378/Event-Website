package uk.ac.man.cs.eventlite.config;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class Security {
    public static final String ADMIN_ROLE = "ADMINISTRATOR";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(antMatcher("/h2-console/**")).denyAll()
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/events/new", "/events/update/**",
                        "/venues/new", "/venues/update/**").hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.GET, "/", "/home", "/home/", "/events", "/events/**",
                        "/venues", "/venues/**", "/api/**", "/webjars/**", "/js/**", "/css/**",
                        "/images/**", "/favicon.ico", "/sign-in", "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/", "/home", "/events", "/events/**",
                        "/venues", "/venues/**", "/api/**", "/webjars/**", "/js/**",
                        "/css/**", "/images/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/events", "/events/", "/events/*",
                        "/events/updateSave/*", "/venues", "/venues/", "/venues/updateSave/*")
                    .hasRole(ADMIN_ROLE)
                .requestMatchers(HttpMethod.DELETE, "/events/*", "/venues/*").hasRole(ADMIN_ROLE)
                .requestMatchers("/api/**").denyAll()
                .anyRequest().denyAll())
            .formLogin(login -> login.loginPage("/sign-in").loginProcessingUrl("/sign-in")
                    .defaultSuccessUrl("/", true).permitAll())
            .logout(logout -> logout.logoutUrl("/sign-out").logoutSuccessUrl("/").permitAll())
            .httpBasic(withDefaults())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), antMatcher("/api/**")))
            // Session/form authentication is supported, so CSRF remains enabled for every write.
            .csrf(withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(Environment environment, PasswordEncoder encoder) {
        String username = environment.getProperty("eventlite.admin.username", "").strip();
        String password = environment.getProperty("eventlite.admin.password", "");
        List<UserDetails> users = new ArrayList<>();
        if (username.isBlank() != password.isBlank()) {
            throw new IllegalStateException("Administrator username and password must both be configured.");
        }
        if (!username.isBlank()) {
            users.add(User.withUsername(username).password(encoder.encode(password)).roles(ADMIN_ROLE).build());
        } else if (environment.acceptsProfiles(Profiles.of("demo"))) {
            // This account exists only after explicitly opting into the local demo profile.
            users.add(User.withUsername("demo-admin").password(encoder.encode("demo-password"))
                    .roles(ADMIN_ROLE).build());
        }
        return new InMemoryUserDetailsManager(users);
    }
}
