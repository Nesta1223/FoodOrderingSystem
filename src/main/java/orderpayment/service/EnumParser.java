package orderpayment.service;

import orderpayment.exception.BusinessException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Converts request strings into enum values, turning bad input into a 400 instead of a 500.
 */
final class EnumParser {

    private EnumParser() {
    }

    static <E extends Enum<E>> E parse(Class<E> type, String value, String fieldName) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException("Invalid " + fieldName + ": " + value
                    + ". Allowed values: " + Arrays.toString(type.getEnumConstants()));
        }
    }
}
