package com.nyaalkaran.erp.poc.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.nyaalkaran.erp.poc", considerNestedRepositories = true)
public class JpaAuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(Actors.current());
    }
}
