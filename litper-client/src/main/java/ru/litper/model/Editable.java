package ru.litper.model;

import java.util.List;

/**
 * Контракт сущности, которую можно редактировать через GUI.
 * Возвращает список ошибок валидации; пустой список означает корректные данные.
 */
public interface Editable {

    List<String> validate();
}