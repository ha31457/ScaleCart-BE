package com.hss.scalecart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hss.scalecart.dto.response.ApiResponse;
import com.hss.scalecart.service.RateLimitService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        Bucket bucket = null;

        if ("POST".equals(method) && uri.equals("/api/v1/orders")) {
            String customerId = (String) request.getAttribute("userId");
            if (customerId != null) {
                bucket = rateLimitService.resolveOrderBucket(customerId);
            }
        } else if ("POST".equals(method) && uri.equals("/api/v1/auth/login")) {
            String ip = getClientIp(request);
            bucket = rateLimitService.resolveLoginBucket(ip);
        } else if ("POST".equals(method) && uri.equals("/api/v1/products")) {
            String sellerId = (String) request.getAttribute("userId");
            if (sellerId != null) {
                bucket = rateLimitService.resolveProductBucket(sellerId);
            }
        }

        if (bucket == null) {
            return true;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.addHeader("X-Rate-Limit-Remaining", "0");

        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error("Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds."));

        log.warn("Rate limit exceeded — uri={}, method={}", uri, method);
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}