package orderpayment.exception;

/**
 * Thrown when a request violates a business rule. Mapped to HTTP 400.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
