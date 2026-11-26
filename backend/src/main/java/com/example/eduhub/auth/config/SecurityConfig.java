package com.example.eduhub.auth.config;

import com.example.eduhub.common.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 토큰 검증은 리소스 서버(oauth2ResourceServer().jwt())에 맡긴다. 직접 만든 필터로 파싱하지 않는다. 권한 규칙은 위에서부터 먼저 맞는 것이 적용되므로
 * 순서가 중요하다(공개 GET이 ADMIN 규칙보다 먼저, anyRequest는 마지막).
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
    AuthenticationEntryPoint entryPoint =
        (request, response, ex) -> {
          response.setHeader("WWW-Authenticate", "Bearer");
          write(
              response,
              objectMapper,
              HttpServletResponse.SC_UNAUTHORIZED,
              "UNAUTHORIZED",
              "로그인이 필요합니다.");
        };
    AccessDeniedHandler deniedHandler =
        (request, response, ex) ->
            write(
                response, objectMapper, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "권한이 없습니다.");

    // 쿠키가 아니라 Authorization 헤더 인증이라 CSRF 대상 아님. 쿠키 인증을 추가하면 다시 검토
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/courses/**")
                .permitAll()
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api/health",
                    "/error")
                .permitAll()
                .requestMatchers("/api/v1/courses/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**")
                .hasRole("ADMIN")
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(oauth ->
            oauth
                .bearerTokenResolver(bearerTokenResolver())
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler));
    return http.build();
  }

  /** role 클레임(EMPLOYEE·ADMIN)을 ROLE_ 권한으로 바꾼다. */
  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("role");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  /** 로그인 요청은 붙어 온 토큰을 읽지 않는다(만료·위조 토큰 때문에 재로그인이 막히지 않게). 그 밖의 경로는 기본 동작(공개 GET도 만료 토큰이면 401). */
  @Bean
  public BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    return request ->
        "POST".equals(request.getMethod()) && "/api/v1/auth/login".equals(request.getRequestURI())
            ? null
            : delegate.resolve(request);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private static void write(
      HttpServletResponse response, ObjectMapper mapper, int status, String code, String message)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    mapper.writeValue(response.getWriter(), new ErrorResponse(code, message));
  }
}
