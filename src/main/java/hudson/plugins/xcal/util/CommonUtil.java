/*
 * Copyright (C) 2019-2020 XC5 Hong Kong Limited, Inc. All Rights Reserved.
 *
 */

package hudson.plugins.xcal.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;

@Slf4j
public final class CommonUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    private CommonUtil() {
    }

    public static String formatString(String string, Object... objects) {
        return MessageFormatter.arrayFormat(string, objects).getMessage();
    }

    public static String writeObjectToJsonStringSilently(Object input) {
        return writeObjectToJsonStringSilently(null, input);
    }

    public static String writeObjectToJsonStringSilently(ObjectMapper om, Object input) {
        log.trace("[writeObjectToJsonStringSilently] class of input: {}", input.getClass().getName());
        String result = input instanceof String ? "" : "{}";
        try {
            if (om == null) {
                om = objectMapper;
            }
            result = om.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            log.error("[writeObjectToJsonStringSilently] Exception, {}: {}", e.getClass(), e.getMessage());
        }
        return result;
    }
}
