package ru.litper.client.dto;

import java.util.List;

public record ProblemDto(
        String type,
        String title,
        Integer status,
        String detail,
        String instance,
        List<String> errors,
        Integer fileVersion) {
}
