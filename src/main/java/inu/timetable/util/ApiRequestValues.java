package inu.timetable.util;

import inu.timetable.exception.ApiException;

import java.util.List;
import java.util.Map;

public final class ApiRequestValues {

    private ApiRequestValues() {
    }

    public static Long requiredLong(Map<String, Object> request, String field) {
        Object value = requiredValue(request, field);
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw ApiException.badRequest(field + " 값이 올바르지 않습니다.");
        }
    }

    public static Integer requiredInteger(Map<String, Object> request, String field) {
        Object value = requiredValue(request, field);
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw ApiException.badRequest(field + " 값이 올바르지 않습니다.");
        }
    }

    public static Integer optionalInteger(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw ApiException.badRequest(field + " 값이 올바르지 않습니다.");
        }
    }

    public static Boolean optionalBoolean(Map<String, Object> request, String field, boolean defaultValue) {
        Object value = request.get(field);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.valueOf(value.toString());
    }

    public static String optionalString(Map<String, Object> request, String field) {
        Object value = request.get(field);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public static List<String> optionalStringList(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof String)) {
                    throw ApiException.badRequest(field + " 값이 올바르지 않습니다.");
                }
            }
            return (List<String>) list;
        }
        throw ApiException.badRequest(field + " 값이 올바르지 않습니다.");
    }

    private static Object requiredValue(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (value == null) {
            throw ApiException.badRequest(field + " 값이 필요합니다.");
        }
        return value;
    }
}
