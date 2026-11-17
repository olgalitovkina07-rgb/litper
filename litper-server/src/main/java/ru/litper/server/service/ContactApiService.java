package ru.litper.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import ru.litper.model.Contact;
import ru.litper.model.ContactType;
import ru.litper.model.CorporateContact;
import ru.litper.model.Editable;
import ru.litper.model.EmergencyContact;
import ru.litper.persistence.CsvLoadResult;
import ru.litper.persistence.CsvLoader;
import ru.litper.persistence.CsvWriter;
import ru.litper.persistence.migration.ContactMigration;
import ru.litper.persistence.serialize.ContactData;
import ru.litper.persistence.serialize.ContactSerializers;
import ru.litper.persistence.serialize.NewerSchemaVersionException;
import ru.litper.persistence.serialize.SchemaVersion;
import ru.litper.persistence.serialize.SerializationException;
import ru.litper.persistence.serialize.Serializer;
import ru.litper.server.dto.ContactFilter;
import ru.litper.server.dto.ContactRequest;
import ru.litper.server.dto.ContactResponse;
import ru.litper.server.dto.CountResponse;
import ru.litper.server.dto.ExportedData;
import ru.litper.server.dto.ImportResponse;
import ru.litper.server.error.BadRequestException;
import ru.litper.server.error.ContactNotEditableException;
import ru.litper.server.error.ContactNotFoundException;
import ru.litper.server.error.InvalidContactException;
import ru.litper.service.ContactGenerator;
import ru.litper.service.ContactService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * REST-сценарии над справочником: DTO, фильтры, валидация и сериализация.
 */
@Service
public class ContactApiService {

    private static final Logger log = LoggerFactory.getLogger(ContactApiService.class);

    private static final String JSON_FORMAT = "json";
    private static final String BINARY_FORMAT = "bin";
    private static final String BINARY_ALIAS = "binary";
    private static final String CSV_FORMAT = "csv";
    private static final String EMPTY = "";
    private static final int DEFAULT_SEARCH_LIMIT = 50;
    private static final int MAX_SEARCH_LIMIT = 200;
    private static final int MAX_GENERATE_COUNT = 1_000_000;

    private final ContactService contacts;
    private final ContactMigration migration = new ContactMigration();

    public ContactApiService(ContactService contacts) {
        this.contacts = contacts;
    }

    public List<ContactResponse> findAll(ContactFilter filter) {
        ContactType type = isBlank(filter.type()) ? null : parseType(filter.type());
        ContactService.ContactEntry[] entries = contacts.entries();
        List<ContactResponse> result = new ArrayList<>();
        for (ContactService.ContactEntry entry : entries) {
            Contact contact = entry.contact();
            if (!matches(contact.getName(), filter.name())
                    || !matches(contact.getOrganization(), filter.organization())
                    || !matches(contact.getPhone(), filter.phone())
                    || !matches(contact.getEmail(), filter.email())
                    || !matches(contact.getNote(), filter.note())
                    || !matches(contact.getTags(), filter.tags())) {
                continue;
            }
            if (type != null && typeOf(contact) != type) {
                continue;
            }
            result.add(ContactResponse.from(entry.id(), contact));
        }
        return result;
    }

    public ContactResponse findById(long id) {
        Contact contact = contacts.getById(id);
        if (contact == null) {
            throw new ContactNotFoundException(id);
        }
        return ContactResponse.from(id, contact);
    }

    public ContactResponse create(ContactRequest request) {
        Contact contact = toContact(request);
        long id = contacts.addUnique(contact);
        return ContactResponse.from(id, contact);
    }

    public ContactResponse update(long id, ContactRequest request) {
        Contact original = contacts.getById(id);
        if (original == null) {
            throw new ContactNotFoundException(id);
        }
        if (!(original instanceof Editable)) {
            throw new ContactNotEditableException(id);
        }

        ContactType requestedType = parseType(request.type());
        if (requestedType != typeOf(original)) {
            throw new InvalidContactException("Тип контакта нельзя изменить");
        }

        Contact updated = toContact(request);
        if (!contacts.updateUnique(original, updated)) {
            throw new ContactNotFoundException(id);
        }
        return ContactResponse.from(id, updated);
    }

    public void delete(long id) {
        Contact contact = contacts.getById(id);
        if (contact == null) {
            throw new ContactNotFoundException(id);
        }
        contacts.remove(contact);
    }

    public List<ContactResponse> search(String prefix, int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_SEARCH_LIMIT : Math.min(limit, MAX_SEARCH_LIMIT);
        Contact[] found = contacts.suggest(prefix, safeLimit);
        List<ContactResponse> result = new ArrayList<>(found.length);
        for (Contact contact : found) {
            result.add(ContactResponse.from(contacts.idOf(contact), contact));
        }
        return result;
    }

    public CountResponse count(String prefix) {
        String safePrefix = prefix == null ? EMPTY : prefix.trim();
        return new CountResponse(safePrefix, contacts.countByPrefix(safePrefix));
    }

    public List<ContactResponse> findByName(String name) {
        String safeName = name == null ? EMPTY : name.trim();
        if (safeName.isEmpty()) {
            return List.of();
        }
        Contact[] found = contacts.findByName(safeName);
        List<ContactResponse> result = new ArrayList<>(found.length);
        for (Contact contact : found) {
            result.add(ContactResponse.from(contacts.idOf(contact), contact));
        }
        return result;
    }

    public int generate(int count, long seed) {
        if (count <= 0) {
            throw new BadRequestException("Количество контактов должно быть положительным");
        }
        if (count > MAX_GENERATE_COUNT) {
            throw new BadRequestException("Слишком много контактов: " + count);
        }
        Contact[] generated = new ContactGenerator().generate(count, seed, null);
        contacts.replaceAll(generated);
        return generated.length;
    }

    public int clear() {
        int removed = contacts.size();
        contacts.clear();
        return removed;
    }

    public int removeByName(String name) {
        String safeName = name == null ? EMPTY : name.trim();
        if (safeName.isEmpty()) {
            throw new BadRequestException("Имя не должно быть пустым");
        }
        int removed = 0;
        for (Contact contact : contacts.findByName(safeName)) {
            if (contacts.remove(contact)) {
                removed++;
            }
        }
        return removed;
    }

    public int removeByPrefix(String prefix) {
        String safePrefix = prefix == null ? EMPTY : prefix.trim();
        if (safePrefix.isEmpty()) {
            throw new BadRequestException("Префикс не должен быть пустым");
        }
        return contacts.removeByPrefix(safePrefix);
    }

    public ExportedData export(String format) throws SerializationException {
        return export(format, null);
    }

    public ExportedData export(String format, Integer version) throws SerializationException {
        if (isCsv(format)) {
            return exportCsv();
        }
        Serializer<ContactData> serializer = serializer(format);
        SchemaVersion target = version == null
                ? SchemaVersion.CURRENT
                : parseVersion(version);
        Path file = null;
        try {
            file = Files.createTempFile("litper-export-", "." + serializer.fileExtension());
            serializer.save(ContactData.of(contacts.getAll()).forSchemaVersion(target), file);
            byte[] content = Files.readAllBytes(file);
            return new ExportedData(
                    content,
                    "contacts." + serializer.fileExtension(),
                    mediaType(serializer));
        } catch (SerializationException e) {
            throw e;
        } catch (IOException e) {
            throw new SerializationException(
                    "Не удалось экспортировать справочник: " + e.getMessage(), e);
        } finally {
            deleteTemp(file);
        }
    }

    public ImportResponse importData(byte[] body, String format) throws SerializationException {
        if (body == null || body.length == 0) {
            throw new BadRequestException("Тело запроса с файлом пустое");
        }
        if (isCsv(format)) {
            return importCsv(body);
        }

        Serializer<ContactData> serializer = serializer(format);
        Path file = null;
        try {
            file = Files.createTempFile("litper-import-", "." + serializer.fileExtension());
            Files.write(file, body);

            ContactData loaded = serializer.load(file);
            int sourceVersion = loaded.schemaVersion();
            if (sourceVersion > ContactData.CURRENT_VERSION) {
                throw new NewerSchemaVersionException(sourceVersion);
            }
            ContactData current = sourceVersion == ContactData.CURRENT_VERSION
                    ? loaded
                    : migration.migrate(loaded);
            Contact[] contactsArray = current.toContacts();
            contacts.replaceAll(contactsArray);
            return new ImportResponse(
                    apiFormat(serializer),
                    sourceVersion,
                    ContactData.CURRENT_VERSION,
                    contactsArray.length,
                    sourceVersion != ContactData.CURRENT_VERSION,
                    0);
        } catch (SerializationException e) {
            throw e;
        } catch (IOException e) {
            throw new SerializationException(
                    "Не удалось импортировать справочник: " + e.getMessage(), e);
        } finally {
            deleteTemp(file);
        }
    }

    private ExportedData exportCsv() throws SerializationException {
        Path file = null;
        try {
            file = Files.createTempFile("litper-export-", ".csv");
            new CsvWriter().write(file, contacts.getAll());
            byte[] content = Files.readAllBytes(file);
            return new ExportedData(content, "contacts.csv", MediaType.parseMediaType("text/csv"));
        } catch (IOException e) {
            throw new SerializationException(
                    "Не удалось экспортировать CSV: " + e.getMessage(), e);
        } finally {
            deleteTemp(file);
        }
    }

    private ImportResponse importCsv(byte[] body) throws SerializationException {
        Path file = null;
        try {
            file = Files.createTempFile("litper-import-", ".csv");
            Files.write(file, body);

            CsvLoadResult result = new CsvLoader().load(file);
            contacts.replaceAll(result.contacts());
            return new ImportResponse(
                    CSV_FORMAT,
                    ContactData.CURRENT_VERSION,
                    ContactData.CURRENT_VERSION,
                    result.contacts().length,
                    false,
                    result.skippedLines());
        } catch (IOException e) {
            throw new SerializationException(
                    "Не удалось импортировать CSV: " + e.getMessage(), e);
        } finally {
            deleteTemp(file);
        }
    }

    private Contact toContact(ContactRequest request) {
        if (request == null) {
            throw new BadRequestException("Тело запроса обязательно");
        }
        ContactType type = parseType(request.type());
        String name = trim(request.name());
        String phone = trim(request.phone());
        if (name.isEmpty() || phone.isEmpty()) {
            throw new InvalidContactException("Имя и телефон обязательны");
        }

        return switch (type) {
            case CORPORATE -> {
                CorporateContact corporate = new CorporateContact(
                        name,
                        phone,
                        orEmpty(request.email()),
                        orEmpty(request.organization()),
                        orEmpty(request.position()),
                        orEmpty(request.internalNumber()),
                        orEmpty(request.note()),
                        orEmpty(request.tags()));
                validate(corporate);
                yield corporate;
            }
            case EMERGENCY -> new EmergencyContact(
                    name,
                    phone,
                    orEmpty(request.email()),
                    orEmpty(request.organization()),
                    orEmpty(request.note()),
                    orEmpty(request.tags()));
        };
    }

    private static void validate(Contact contact) {
        if (contact instanceof Editable editable) {
            List<String> errors = editable.validate();
            if (!errors.isEmpty()) {
                throw new InvalidContactException(String.join("; ", errors), errors);
            }
        }
    }

    private static ContactType parseType(String type) {
        if (isBlank(type)) {
            throw new BadRequestException("Тип контакта обязателен");
        }
        try {
            return ContactType.fromCsvId(type.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Неизвестный тип контакта: " + type);
        }
    }

    private static ContactType typeOf(Contact contact) {
        return contact instanceof CorporateContact
                ? ContactType.CORPORATE
                : ContactType.EMERGENCY;
    }

    private static boolean matches(String value, String query) {
        if (isBlank(query)) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT)
                .contains(query.trim().toLowerCase(Locale.ROOT));
    }

    private static Serializer<ContactData> serializer(String format) {
        String normalized = format == null ? JSON_FORMAT : format.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case JSON_FORMAT -> ContactSerializers.json();
            case BINARY_FORMAT, BINARY_ALIAS -> ContactSerializers.binary();
            default -> throw new BadRequestException(
                    "Неизвестный формат: " + format + ". Допустимо: json, bin, csv");
        };
    }

    private static boolean isCsv(String format) {
        return format != null && CSV_FORMAT.equalsIgnoreCase(format.trim());
    }

    private static SchemaVersion parseVersion(int version) {
        try {
            return SchemaVersion.of(version);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Неизвестная версия схемы: " + version);
        }
    }

    private static MediaType mediaType(Serializer<ContactData> serializer) {
        return JSON_FORMAT.equals(serializer.format())
                ? MediaType.APPLICATION_JSON
                : MediaType.APPLICATION_OCTET_STREAM;
    }

    private static String apiFormat(Serializer<ContactData> serializer) {
        return JSON_FORMAT.equals(serializer.format()) ? JSON_FORMAT : BINARY_FORMAT;
    }

    private static void deleteTemp(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Не удалось удалить временный файл {}", file, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trim(String value) {
        return value == null ? EMPTY : value.trim();
    }

    private static String orEmpty(String value) {
        return value == null ? EMPTY : value;
    }
}
