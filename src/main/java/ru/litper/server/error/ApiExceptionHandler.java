package ru.litper.server.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.litper.persistence.serialize.NewerSchemaVersionException;
import ru.litper.persistence.serialize.SerializationException;
import ru.litper.service.DuplicateContactException;

import java.util.List;

/**
 * Единый формат ошибок REST API — ProblemDetail (RFC 9457).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ContactNotFoundException.class)
    public ProblemDetail notFound(ContactNotFoundException error) {
        return problem(HttpStatus.NOT_FOUND, "Контакт не найден", error.getMessage());
    }

    @ExceptionHandler(ContactNotEditableException.class)
    public ProblemDetail notEditable(ContactNotEditableException error) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Контакт нельзя изменить", error.getMessage());
    }

    @ExceptionHandler(DuplicateContactException.class)
    public ProblemDetail conflict(DuplicateContactException error) {
        return problem(HttpStatus.CONFLICT, "Конфликт данных", error.getMessage());
    }

    @ExceptionHandler(NewerSchemaVersionException.class)
    public ProblemDetail newerVersion(NewerSchemaVersionException error) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT,
                "Версия файла не поддерживается", error.getMessage());
        detail.setProperty("fileVersion", error.getFileVersion());
        return detail;
    }

    @ExceptionHandler(InvalidContactException.class)
    public ProblemDetail invalidContact(InvalidContactException error) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST,
                "Ошибка валидации", error.getMessage());
        detail.setProperty("errors", error.getErrors());
        return detail;
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException error) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректный запрос", error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException error) {
        List<String> errors = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .toList();
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST,
                "Ошибка валидации", String.join("; ", errors));
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraint(ConstraintViolationException error) {
        List<String> errors = error.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST,
                "Ошибка валидации", String.join("; ", errors));
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException error) {
        return problem(HttpStatus.BAD_REQUEST,
                "Некорректный JSON", "Тело запроса не удалось прочитать");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail missingParameter(MissingServletRequestParameterException error) {
        return problem(HttpStatus.BAD_REQUEST,
                "Не хватает параметра", "Параметр «" + error.getParameterName() + "» обязателен");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail typeMismatch(MethodArgumentTypeMismatchException error) {
        String expected = error.getRequiredType() == null
                ? "другого типа"
                : error.getRequiredType().getSimpleName();
        return problem(HttpStatus.BAD_REQUEST,
                "Некорректный параметр",
                "Параметр «" + error.getName() + "» должен быть типа " + expected);
    }

    @ExceptionHandler(SerializationException.class)
    public ProblemDetail serialization(SerializationException error) {
        return problem(HttpStatus.BAD_REQUEST,
                "Ошибка файла", error.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception error) {
        log.error("Необработанная ошибка REST API", error);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Внутренняя ошибка", "Не удалось выполнить запрос");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
