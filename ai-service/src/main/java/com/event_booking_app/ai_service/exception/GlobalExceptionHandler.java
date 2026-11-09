package com.event_booking_app.ai_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AiProviderException.class)
    ProblemDetail handleAiProvider(AiProviderException ex) {
        log.error("AI provider failure", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The AI assistant is temporarily unavailable. Please try again later.");
        problem.setTitle("AI service unavailable");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
        problem.setTitle("Internal server error");
        return problem;
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    ProblemDetail handleConversationNotFound(ConversationNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Conversation not found");
        return problem;
    }

    @ExceptionHandler(InvalidTokenException.class)
    ProblemDetail handleInvalidToken(InvalidTokenException ex) {
        log.warn("Rejected request with invalid token subject: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication token is invalid.");
        problem.setTitle("Unauthorized");
        return problem;
    }
}

