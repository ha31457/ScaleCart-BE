package com.hss.scalecart.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class MdcTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Use incoming trace ID if provided (e.g. from upstream service), else generate new one
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }

            MDC.put(TRACE_ID_MDC_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);

            filterChain.doFilter(request, response);

            // Pull userId into MDC after JwtAuthFilter has run
            String userId = (String) request.getAttribute("userId");
            if (userId != null) {
                MDC.put("customerId", userId);
            }
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove("customerId");
            MDC.remove("orderId");
        }
    }
}