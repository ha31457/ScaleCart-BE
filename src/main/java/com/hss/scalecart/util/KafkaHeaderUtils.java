package com.hss.scalecart.util;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

public class KafkaHeaderUtils {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    public static void injectTraceId(Headers headers) {
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            headers.add(TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static String extractTraceId(ConsumerRecord<?, ?> record) {
        var header = record.headers().lastHeader(TRACE_ID_HEADER);
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }
}