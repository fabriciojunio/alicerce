package br.com.alicerce.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A política de segurança do serviço, num lugar só.
 *
 * <p>Negação por padrão: toda rota exige token, menos a saúde. O token é um
 * JWT emitido pelo provedor de identidade (Keycloak no compose); o serviço não
 * guarda senha nem emite token.
 */
@Configuration
public class ConfiguracaoDeSeguranca {

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http, CorsConfigurationSource cors) throws Exception {
        http
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/admin/**").hasRole("admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(papeisDoKeycloak())))
                // Sem sessão no servidor e sem cookie: o token vai no cabeçalho
                // Authorization, que o navegador não anexa sozinho. É isso que
                // torna o CSRF inaplicável aqui; se um dia entrar cookie, ele volta.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(cors))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")));
        return http.build();
    }

    @Bean
    CorsConfigurationSource cors(PropriedadesDeSeguranca propriedades) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(propriedades.origensPermitidas());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        config.setMaxAge(600L);
        var fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", config);
        return fonte;
    }

    /**
     * O Keycloak põe os papéis em {@code realm_access.roles}. O Spring espera
     * autoridades com prefixo {@code ROLE_}; este conversor faz a ponte.
     */
    static Converter<Jwt, AbstractAuthenticationToken> papeisDoKeycloak() {
        return jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            Collection<?> papeis = realm == null ? List.of() : (Collection<?>) realm.getOrDefault("roles", List.of());
            List<GrantedAuthority> autoridades = papeis.stream()
                    .map(String::valueOf)
                    .<GrantedAuthority>map(papel -> new SimpleGrantedAuthority("ROLE_" + papel))
                    .toList();
            return new JwtAuthenticationToken(jwt, autoridades, jwt.getSubject());
        };
    }
}
