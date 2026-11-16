# PLAN5.md — план лабораторной работы №5

## Вариант

**Вариант 4 — «Справочник контактов».**

## Цель

Поднять Spring Boot-сервер в том же процессе, что и JavaFX-приложение, и
предоставить REST API над существующим `ContactService` с префиксным деревом.
GUI работает с тем же singleton-бином напрямую и обновляется автоматически
после изменений через API.

## Архитектура

```text
JavaFX MainApp ──> ContactService (Spring @Service)
                         ▲
Spring MVC Controller ───┘
        │
        └──> ContactApiService (DTO, фильтры, ошибки)
```

- `ContactService` остаётся хранилищем и бизнес-сервисом, дополнительно получает
  числовые ID, слушателей изменений и потокобезопасные операции.
- `ContactApiService` — слой REST-сценариев: преобразование DTO, валидация,
  конфликты, экспорт/импорт.
- `controller` — только HTTP: параметры, статусы, `Location`, `ProblemDetail`.
- Spring Boot запускается в `MainApp.init()`, JavaFX — в `start()`.
- JPMS убирается: Spring Boot и module path плохо совместимы, проект переходит
  на обычный classpath.

## Этап 1. Spring Boot и сборка

- [x] Подключить Spring Boot `3.5.16` (Web, Validation, Test).
- [x] Удалить `module-info.java`, отключить `inferModulePath` и `mainModule`.
- [x] Добавить `ServerApp` с `@SpringBootApplication`.
- [x] Сделать `ContactService` бином `@Service`.
- [x] Проверить `gradlew.bat run` и `gradlew.bat bootRun`.

## Этап 2. ID и слушатели в сервисе

- [x] Числовые ID контактов без изменения модели и файловых форматов.
- [x] `entries()`, `getById()`, `idOf()` для REST.
- [x] Сохранение ID при `update`, удаление при `remove`/`removeByPrefix`.
- [x] `ContactChangeListener` и уведомления после всех изменений.
- [x] Потокобезопасность операций с ID и деревом.

## Этап 3. REST CRUD

- [x] `GET /api/contacts` с фильтрами `name`, `organization`, `type`,
      `phone`, `email`, `note`, `tags`.
- [x] `GET /api/contacts/{id}`.
- [x] `POST /api/contacts` → `201` + `Location`.
- [x] `PUT /api/contacts/{id}` только для `Editable`, иначе `422`.
- [x] `DELETE /api/contacts/{id}` → `204` / `404`.
- [x] Проверка дубликата имя + телефон → `409`.

## Этап 4. Операции варианта и сериализация

- [x] `GET /api/contacts/search?prefix=...&limit=...` — автодополнение.
- [x] `GET /api/contacts/count?prefix=...` — контакты в поддереве.
- [x] `POST /api/export?format=json|bin` — файл в ответе.
- [x] `POST /api/import?format=json|bin` — загрузка из тела запроса.
- [x] Автомиграция старых файлов через `ContactMigration`.
- [x] `VERSION_TOO_NEW` из ЛР4 → `409`.

## Этап 5. Ошибки ProblemDetail

- [x] `400` — валидация, битые параметры, повреждённый файл.
- [x] `404` — контакт не найден.
- [x] `409` — дубликат и версия файла из будущего.
- [x] `422` — попытка изменить не-`Editable`.
- [x] Единый `@RestControllerAdvice` без перехвата исключений «в пустоту».

## Этап 6. GUI и автоматическое обновление

- [x] Spring-контекст поднимается в `MainApp.init()`.
- [x] GUI получает общий бин `ContactService`, а не создаёт свой.
- [x] Кнопка «Обновить».
- [x] Подписка на изменения сервиса и `Platform.runLater` для обновления таблицы
      и панели дерева после запросов к API.
- [x] Статус с адресом REST API.

## Этап 7. Тесты и документация

- [x] Тесты ID, `update`, `removeByPrefix`, слушателей.
- [x] MockMvc-тесты CRUD, фильтров, поиска, `Location`, `422`, `404`, `409`.
- [x] Тесты export/import JSON и bin, включая версию из будущего.
- [x] README: запуск, endpoints, примеры `curl`, форматы, автообновление.
- [x] `gradlew.bat test` и `gradlew.bat build` зелёные.

## Итоговый критерий

- [x] Сервер и GUI работают в одном процессе.
- [x] CRUD и операции варианта доступны по REST.
- [x] Ошибки возвращаются в `ProblemDetail`.
- [x] GUI обновляется автоматически после вызова API.
- [x] Экспорт/импорт и миграции работают через REST.
- [x] Все тесты проходят.
