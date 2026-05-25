package com.hss.scalecart.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
public class CursorUtils {

    private static final String DELIMITER = "::";

    public static String encode(Instant createdAt, UUID id) {
        String raw = createdAt.toString() + DELIMITER + id.toString();
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Instant decodeCreatedAt(String cursor) {
        String raw = decode(cursor);
        return Instant.parse(raw.split(DELIMITER)[0]);
    }

    public static UUID decodeId(String cursor) {
        String raw = decode(cursor);
        return UUID.fromString(raw.split(DELIMITER)[1]);
    }

    private static String decode(String cursor) {
        return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    }
}