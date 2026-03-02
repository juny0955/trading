package dev.junyoung.trading.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 모든 HTTP 요청에 고유한 traceId를 부여하고 MDC에 등록한다.
 * <p>응답 헤더 {@code X-Trace-Id}로도 노출하여 클라이언트가 식별할 수 있도록 한다.</p>
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    static final String TRACE_ID_KEY = "traceId";
    static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
