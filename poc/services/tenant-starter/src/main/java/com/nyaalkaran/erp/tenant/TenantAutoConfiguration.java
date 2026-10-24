package com.nyaalkaran.erp.tenant;

import com.nyaalkaran.erp.tenant.access.CompanyStatusRule;
import com.nyaalkaran.erp.tenant.access.ModuleAccessRule;
import com.nyaalkaran.erp.tenant.access.ModuleEntitlementRule;
import com.nyaalkaran.erp.tenant.access.RedisRuntimeStateReader;
import com.nyaalkaran.erp.tenant.access.RuntimeStateReader;
import com.nyaalkaran.erp.tenant.access.TenantAccessInterceptor;
import com.nyaalkaran.erp.tenant.access.TenantAccessRule;
import com.nyaalkaran.erp.tenant.controlplane.ClientCredentialsTokenProvider;
import com.nyaalkaran.erp.tenant.controlplane.ControlPlaneClient;
import com.nyaalkaran.erp.tenant.controlplane.ServiceTokenProvider;
import com.nyaalkaran.erp.tenant.outbound.TenantRestClients;
import com.nyaalkaran.erp.tenant.routing.ControlPlaneDatabaseLocator;
import com.nyaalkaran.erp.tenant.routing.TenantDataSourceRegistry;
import com.nyaalkaran.erp.tenant.routing.TenantDatabaseLocator;
import com.nyaalkaran.erp.tenant.routing.TenantJdbc;
import com.nyaalkaran.erp.tenant.security.CompanyIssuerRegistry;
import com.nyaalkaran.erp.tenant.security.CompanyRealmAuthenticationManagerResolver;
import com.nyaalkaran.erp.tenant.security.ControlPlaneCompanyIssuerRegistry;
import com.nyaalkaran.erp.tenant.web.ServiceExceptionHandler;
import com.nyaalkaran.erp.tenant.web.TenantRoutingInfoController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.List;

/**
 * Makes any Spring Boot service a tenant-aware ERP domain service by adding this one dependency. Every bean is
 * an implementation of a small interface and can be replaced (@ConditionalOnMissingBean), e.g. a gateway-header
 * based registry or a different RuntimeStateReader, without touching the services that use them.
 * Services exclude DataSourceAutoConfiguration: there is no static datasource, only TenantJdbc (master §15.3).
 */
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@EnableConfigurationProperties(TenantProperties.class)
@EnableWebSecurity
public class TenantAutoConfiguration {

    // ------------------------------------------------------------ control plane (M2M)

    @Bean
    @ConditionalOnMissingBean
    ServiceTokenProvider serviceTokenProvider(TenantProperties props, RestClient.Builder builder) {
        return new ClientCredentialsTokenProvider(props.keycloak(), builder);
    }

    @Bean
    @ConditionalOnMissingBean
    ControlPlaneClient controlPlaneClient(TenantProperties props, ServiceTokenProvider tokens,
                                          RestClient.Builder builder) {
        return new ControlPlaneClient(props, tokens, builder);
    }

    // ------------------------------------------------------------ token verification

    @Bean
    @ConditionalOnMissingBean
    CompanyIssuerRegistry companyIssuerRegistry(ControlPlaneClient controlPlane, TenantProperties props) {
        return new ControlPlaneCompanyIssuerRegistry(controlPlane, props);
    }

    @Bean
    @ConditionalOnMissingBean
    CompanyRealmAuthenticationManagerResolver companyRealmAuthenticationManagerResolver(
            CompanyIssuerRegistry registry, TenantProperties props) {
        return new CompanyRealmAuthenticationManagerResolver(registry, props);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain tenantSecurityFilterChain(HttpSecurity http,
                                                  CompanyRealmAuthenticationManagerResolver companyRealms,
                                                  TenantProperties props)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsSource(props)))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /error: Spring's error dispatch must render the real status, not a 403 of its own
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.authenticationManagerResolver(
                        new JwtIssuerAuthenticationManagerResolver(companyRealms)));
        return http.build();
    }

    /** Browser origins of the tenant portal (erp.tenant.cors-allowed-origins). */
    private static CorsConfigurationSource corsSource(TenantProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    // ------------------------------------------------------------ tenant gate

    @Bean
    @ConditionalOnMissingBean
    RuntimeStateReader runtimeStateReader(StringRedisTemplate redis) {
        return new RedisRuntimeStateReader(redis);
    }

    @Bean
    CompanyStatusRule companyStatusRule(RuntimeStateReader state) {
        return new CompanyStatusRule(state);
    }

    @Bean
    ModuleEntitlementRule moduleEntitlementRule(RuntimeStateReader state) {
        return new ModuleEntitlementRule(state);
    }

    @Bean
    ModuleAccessRule moduleAccessRule() {
        return new ModuleAccessRule();
    }

    @Bean
    TenantAccessInterceptor tenantAccessInterceptor(TenantProperties props, List<TenantAccessRule> rules) {
        if (props.moduleCode() == null || props.gatewayToken() == null) {
            throw new IllegalStateException("erp.tenant.module-code and erp.tenant.gateway-token are required");
        }
        return new TenantAccessInterceptor(props, rules);
    }

    @Bean
    WebMvcConfigurer tenantAccessWebMvcConfigurer(TenantAccessInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/api/**");
            }
        };
    }

    @Bean
    ServiceExceptionHandler serviceExceptionHandler() {
        return new ServiceExceptionHandler();
    }

    // ------------------------------------------------------------ routing and outbound calls

    @Bean
    @ConditionalOnMissingBean
    TenantDatabaseLocator tenantDatabaseLocator(ControlPlaneClient controlPlane, TenantProperties props) {
        return new ControlPlaneDatabaseLocator(controlPlane, props);
    }

    @Bean
    TenantDataSourceRegistry tenantDataSourceRegistry(TenantProperties props) {
        return new TenantDataSourceRegistry(props);
    }

    @Bean
    TenantJdbc tenantJdbc(TenantDatabaseLocator locator, TenantDataSourceRegistry dataSources) {
        return new TenantJdbc(locator, dataSources);
    }

    @Bean
    TenantRestClients tenantRestClients(RestClient.Builder builder) {
        return new TenantRestClients(builder);
    }

    @Bean
    TenantRoutingInfoController tenantRoutingInfoController(TenantJdbc jdbc, TenantProperties props) {
        return new TenantRoutingInfoController(jdbc, props);
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
