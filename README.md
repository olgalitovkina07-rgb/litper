# Справочник контактов

Лабораторная работа № 1 «Разработка модели данных» — **вариант 4 «Справочник контактов»**.

JavaFX-приложение для ведения справочника контактов: загрузка и сохранение CSV,
отображение в таблице, добавление и редактирование контактов.

## Требования

- JDK 21+ (рекомендуется 25) — проверено на JDK 25
- Остальные зависимости подтягивает Gradle Wrapper автоматически

## Запуск

```bash
./gradlew run          # macOS/Linux
gradlew.bat run        # Windows
```

Сборка и тесты:

```bash
./gradlew build
```

Проект также можно открыть и запустить в IntelliJ IDEA: `Main.java` → «Run».

## Формат CSV

Файл UTF-8, первая строка — заголовок, разделитель полей — точка с запятой `;`.
Один контакт — одна строка. Значения с символом `;` внутри не поддерживаются.

```text
type;name;phone;email;organization;position;internalNumber
corporate;Иван Иванов;+79990000000;ivan@example.com;ООО Ромашка;Инженер;123
emergency;Пожарная служба;101;;МЧС;;
```

| Поле | Значение |
|---|---|
| `type` | `corporate` (корпоративный) или `emergency` (аварийный) |
| `name`, `phone` | обязательные для обоих типов |
| `email`, `organization` | необязательные |
| `position`, `internalNumber` | только для `corporate`; внутренний номер — только цифры |

Битые строки (неверное число полей, неизвестный тип, нечисловой внутренний номер,
пустые обязательные поля) при загрузке **пропускаются** — одна битая строка не
прерывает загрузку всего файла.

## Модель

```text
Contact
├── EmergencyContact       // read-only, появляется только из CSV
└── CorporateContact       // implements Editable (должность, внутренний номер)
```

`Editable` — контракт «сущность можно изменять» с методом `validate()`.
Кнопка «Изменить» в GUI активна только для `CorporateContact`; через «Добавить»
создаётся только корпоративный контакт.

## Структура проекта

```text
src/main/java/ru/litper/
  model/        Contact, EmergencyContact, CorporateContact, Editable, ContactType
  persistence/  CsvLoader (чтение + пропуск битых строк), CsvWriter, ошибки разбора
  service/      ContactService (хранение контактов, без зависимости от JavaFX)
  gui/          MainApp, MainController, ContactDialog
  Main.java     точка входа
src/test/java/  JUnit 5 тесты (модель, CSV)
data/           contacts.csv — пример данных
```

## Технологии

- Java 21+/25
- Gradle (Kotlin DSL) + Gradle Wrapper
- JavaFX 25 (OpenJFX)
- JUnit 5