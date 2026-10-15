package uk.ac.man.cs.eventlite.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock eventliteClock() {
        return Clock.systemDefaultZone();
    }
}
