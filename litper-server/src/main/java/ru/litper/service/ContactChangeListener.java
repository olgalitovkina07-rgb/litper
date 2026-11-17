package ru.litper.service;

/**
 * Получает уведомление после любого изменения справочника.
 *
 * <p>GUI подписывается на изменения, чтобы автоматически обновлять таблицу
 * после запросов к REST API.</p>
 */
@FunctionalInterface
public interface ContactChangeListener {

    void onContactsChanged();
}
