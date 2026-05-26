package de.gaalop.rest.api;

import de.gaalop.CompilationException;
import de.gaalop.rest.dto.CompileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CompileResponse> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest()
                .body(CompileResponse.error("400", error.getMessage()));
    }

    @ExceptionHandler(CompilationException.class)
    public ResponseEntity<CompileResponse> compilationError(CompilationException error) {
        return ResponseEntity.badRequest()
                .body(CompileResponse.error("400", error.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CompileResponse> notFound(NoResourceFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(CompileResponse.error("404", "Endpoint not found."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CompileResponse> internalError(Exception error) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CompileResponse.error("500", error.getMessage()));
    }
}
