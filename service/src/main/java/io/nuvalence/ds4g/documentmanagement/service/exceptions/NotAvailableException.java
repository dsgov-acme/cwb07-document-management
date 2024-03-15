package io.nuvalence.ds4g.documentmanagement.service.exceptions;

/**
 * Exception thrown when a resource is not available.
 */
public class NotAvailableException extends RuntimeException {
    private static final long serialVersionUID = 1234567890L;

    public NotAvailableException(String message) {
        super(message);
    }
}
