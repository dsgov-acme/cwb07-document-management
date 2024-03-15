package io.nuvalence.ds4g.documentmanagement.service.controller;

import io.nuvalence.ds4g.documentmanagement.service.exceptions.NotAvailableException;
import io.nuvalence.ds4g.documentmanagement.service.exceptions.ProvidedDataException;
import io.nuvalence.ds4g.documentmanagement.service.model.ErrorCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

import java.util.List;
import java.util.Objects;

class GlobalErrorHandlerTest {
    private GlobalErrorHandler globalErrorHandler;

    @BeforeEach
    void setUp() {
        globalErrorHandler = new GlobalErrorHandler();
    }

    @Test
    void testAccessDeniedException() {
        ResponseEntity<GlobalErrorHandler.ErrorResponse> responseEntity =
                globalErrorHandler.handleAccessDeniedExceptionException(
                        new AccessDeniedException("Access Denied"));
        Assertions.assertEquals(HttpStatus.FORBIDDEN, responseEntity.getStatusCode());
    }

    @Test
    void testUnsupportedMediaTypeStatusException() {
        ResponseEntity<GlobalErrorHandler.UnsupportedMediaTypeErrorResponse> responseEntity =
                globalErrorHandler.handleMediaTypeNotSupportedException(
                        new UnsupportedMediaTypeStatusException(
                                "Unsupported Media Type",
                                List.of(MediaType.APPLICATION_OCTET_STREAM)));
        Assertions.assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, responseEntity.getStatusCode());
        Assertions.assertEquals(
                ErrorCode.UNSUPPORTED_TYPE.toString(),
                Objects.requireNonNull(responseEntity.getBody()).getErrorCode());
        Assertions.assertEquals(
                ErrorCode.UNSUPPORTED_TYPE.getMessage(), responseEntity.getBody().getMessage());
    }

    @Test
    void testProvidedDataException() {
        ResponseEntity<GlobalErrorHandler.ErrorResponse> responseEntity =
                globalErrorHandler.handleProvidedDataException(
                        new ProvidedDataException("Provided Data Exception"));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Assertions.assertTrue(
                Objects.requireNonNull(responseEntity.getBody())
                        .getMessages()
                        .contains("Provided Data Exception"));
    }

    @Test
    void testNotAvailableException() {
        ResponseEntity<GlobalErrorHandler.ErrorResponse> responseEntity =
                globalErrorHandler.handleNotAvailableException(
                        new NotAvailableException("Not Available Exception"));
        Assertions.assertEquals(HttpStatus.GONE, responseEntity.getStatusCode());
        Assertions.assertTrue(
                Objects.requireNonNull(responseEntity.getBody())
                        .getMessages()
                        .contains("Not Available Exception"));
    }

    @Test
    void testResponseStatusException() {
        ResponseEntity<GlobalErrorHandler.ErrorResponse> responseEntity =
                globalErrorHandler.handleResponseStatusException(
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad Request"));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        Assertions.assertTrue(
                Objects.requireNonNull(responseEntity.getBody())
                        .getMessages()
                        .contains("Bad Request"));
    }

    @Test
    void testUnexpectedException() {
        ResponseEntity<GlobalErrorHandler.ErrorResponse> responseEntity =
                globalErrorHandler.handleException(new UnexpectedException("Unexpected Exception"));
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        // we should not be divulging the exception message to the client
        Assertions.assertFalse(
                Objects.requireNonNull(responseEntity.getBody())
                        .getMessages()
                        .contains("Unexpected Exception"));
    }

    /**
     * Exception to test the unexpected exception handler.
     *
     * <p>With this Exception being private to this test, there is no risk that we add a specific
     * handler for it in the future.</p>
     */
    private static class UnexpectedException extends Exception {
        private static final long serialVersionUID = 1L;

        public UnexpectedException(String message) {
            super(message);
        }
    }
}
