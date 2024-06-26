package net.ow.shared.errorutils.errors;

import java.nio.file.AccessDeniedException;
import javax.naming.AuthenticationException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import net.ow.shared.errorutils.dto.APIResponse;
import net.ow.shared.errorutils.dto.Error;
import net.ow.shared.errorutils.mapper.ErrorMapper;
import net.ow.shared.errorutils.util.LocaleMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Component
@RestControllerAdvice
public class ControllerAdvice {
    private static final Logger log = LoggerFactory.getLogger(ErrorMapper.class);

    private final LocaleMessageSource messages;

    private final ErrorMapper errorMapper;

    public ControllerAdvice(final LocaleMessageSource messages, final ErrorMapper errorMapper) {
        this.messages = messages;
        this.errorMapper = errorMapper;
    }

    /**
     * Handles application-specific exceptions
     */
    @ExceptionHandler({APIException.class})
    public ResponseEntity<APIResponse> handleAPIException(APIException e) {
        if (e.getError().getStatus().is5xxServerError()) {
            log.error("Application threw APIException", e);
        } else {
            log.info("Application threw APIException", e);
        }

        return toResponseEntity(e.getError().getStatus(), errorMapper.toError(e, messages));
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.info("HTTP message not readable", e);
        var status = HttpStatus.BAD_REQUEST;
        return toResponseEntity(status, errorMapper.toError(e, "movie-theatre-400-not-readable"));
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<APIResponse> onAuthenticationNotFound(
            HttpServletRequest request, AuthenticationCredentialsNotFoundException e) {
        log.info("Spring Security threw AuthenticationCredentialsNotFoundException: {}", e.getMessage());

        var auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (null != auth && auth.length() > 20) {
            auth = auth.substring(0, 20) + "...";
        }

        log.info("Auth header: {}", auth);
        log.info(
                "Unauthorised request for {} {} from {}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());

        var status = HttpStatus.UNAUTHORIZED;
        return toResponseEntity(status, errorMapper.toError(e, "movie-theatre-" + status + "-auth", "Unauthorised"));
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public ResponseEntity<APIResponse> onDataAccessException(HttpServletRequest request, RuntimeException e) {
        log.warn("Spring Security threw {}: {}", e.getClass().getSimpleName(), e.getMessage());
        log.warn(
                "Unidentified user denied access to {} {} from {}",
                request.getMethod(),
                request.getPathInfo(),
                request.getRemoteAddr());
        // TODO:
        //        try {
        //            var user = // get user using token helper
        //            log.warn("User '{}' denied access to {} {} from {}", user.getUserId, request.getMethod(),
        // request.getPathInfo(), request.getRemoteAddr());
        //        } catch (Exception ex) {
        //            log.warn("Unidentified user denied access to {} {} from {}", request.getMethod(),
        // request.getPathInfo(), request.getRemoteAddr());
        //        }

        var status = HttpStatus.FORBIDDEN;
        return toResponseEntity(
                status, errorMapper.toError(e, status, "-movie-theatre-access_denied", "Access Denied"));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<APIResponse> handleConstraintViolationException(ConstraintViolationException e) {
        log.info("Application threw ConstraintViolationException", e);
        var status = HttpStatus.BAD_REQUEST;
        return toResponseEntity(status, errorMapper.toError(e));
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<APIResponse> onRuntimeException(RuntimeException e) {
        log.error("Application threw RuntimeException", e);
        return toResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, errorMapper.toError(e));
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse> onException(Exception e) {
        log.error("Application threw Exception", e);
        return toResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, errorMapper.toError(e));
    }

    private ResponseEntity<APIResponse> toResponseEntity(HttpStatus status, Error... errors) {
        return ResponseEntity.status(status).body(APIResponse.error(errors));
    }
}
