package uk.ac.man.cs.eventlite.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class SecurityConfigurationTest {
    private final Security security = new Security();

    @Test
    void productionHasNoDefaultAdministrator() {
        var manager = security.userDetailsService(new MockEnvironment(), security.passwordEncoder());
        assertThrows(UsernameNotFoundException.class, () -> manager.loadUserByUsername("demo-admin"));
    }

    @Test
    void configuredPasswordIsHashedAndAdministratorRoleIsExplicit() {
        var environment = new MockEnvironment().withProperty("eventlite.admin.username", "configured-admin")
                .withProperty("eventlite.admin.password", "configured-password");
        var encoder = security.passwordEncoder();
        var user = security.userDetailsService(environment, encoder).loadUserByUsername("configured-admin");
        assertNotEquals("configured-password", user.getPassword());
        assertTrue(encoder.matches("configured-password", user.getPassword()));
        assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR")));
    }

    @Test
    void partialAdministratorConfigurationFailsClearly() {
        assertThrows(IllegalStateException.class, () -> security.userDetailsService(
                new MockEnvironment().withProperty("eventlite.admin.username", "configured-admin"),
                security.passwordEncoder()));
    }

    @Test
    void predictableDemoAccountRequiresExplicitDemoProfile() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("demo");
        var user = security.userDetailsService(environment, security.passwordEncoder()).loadUserByUsername("demo-admin");
        assertTrue(security.passwordEncoder().matches("demo-password", user.getPassword()));
    }
}
