package com.nirvana.application.utils;

// src/main/java/com/nirvana/application/util/JsonUtils.java

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.util.Collections;
import java.util.List;

/**
 * Minimal JSON helper for reading stringified JSON arrays into List<String>.
 * You can replace this with your existing shared mapper/util if you have one.
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {}

    @SneakyThrows
    public static List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return MAPPER.readValue(json, new TypeReference<List<String>>() {});
    }
}

