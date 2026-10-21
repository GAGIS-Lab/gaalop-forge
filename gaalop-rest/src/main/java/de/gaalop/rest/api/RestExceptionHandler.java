package de.gaalop.rest.api;

import de.gaalop.CompilationException;
import de.gaalop.rest.dto.CompileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CompileResponse> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CompileResponse.error("400", error.getMessage()));
    }

    @ExceptionHandler(CompilationException.class)
    public ResponseEntity<CompileResponse> compilationError(CompilationException error) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CompileResponse.error("400", error.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CompileResponse> notFound(NoResourceFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                // Error payloads use the API's JSON format even for HTML/image requests.
                .contentType(MediaType.APPLICATION_JSON)
                .body(CompileResponse.error("404", "Endpoint not found."));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> notAcceptable(HttpMediaTypeNotAcceptableException error) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CompileResponse> unreadableRequest(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(CompileResponse.error("400", "Request body is missing or contains invalid JSON."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CompileResponse> internalError(Exception error) {
        // Preserve Spring MVC client errors and headers such as Allow (405)
        // and Accept (415), rather than classifying them as internal failures.
        if (error instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
            String detail = response.getBody().getDetail();
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(CompileResponse.error(Integer.toString(response.getStatusCode().value()),
                            detail == null ? "Request rejected." : detail));
        }
        LOGGER.error("Unhandled REST API error", error);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(CompileResponse.error("500", "Internal server error."));
    }
}
