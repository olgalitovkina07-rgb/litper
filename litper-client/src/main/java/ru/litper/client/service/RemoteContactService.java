package ru.litper.client.service;

import ru.litper.client.api.ApiClient;
import ru.litper.client.dto.ContactDto;
import ru.litper.client.dto.ImportResultDto;
import ru.litper.model.Contact;
import ru.litper.model.ContactType;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;
import ru.litper.structure.KeyExtractor;
import ru.litper.structure.Trie;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class RemoteContactService {

    private static final KeyExtractor<Contact> BY_NAME =
            contact -> contact.getName().toLowerCase(Locale.ROOT);

    private final ApiClient api;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<Contact, Long> ids = new IdentityHashMap<>();

    private Contact[] contacts = new Contact[0];
    private Trie<Contact> index = new Trie<>(Contact.class, BY_NAME);

    public RemoteContactService(ApiClient api) {
        this.api = api;
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void refresh() {
        reload(api.list(null, null, null));
    }

    public synchronized Contact[] getAll() {
        return contacts.clone();
    }

    public synchronized int size() {
        return contacts.length;
    }

    public synchronized Trie<Contact> index() {
        return index;
    }

    public synchronized Contact[] suggest(String prefix, int limit) {
        return index.autocomplete(normalize(prefix), limit);
    }

    public synchronized Contact[] findByName(String name) {
        return index.findAll(normalize(name));
    }

    public synchronized int countByPrefix(String prefix) {
        return index.countByPrefix(normalize(prefix));
    }

    public void add(Contact contact) {
        api.create(toDto(contact));
        refresh();
    }

    public boolean update(Contact original, Contact updated) {
        Long id = idOf(original);
        if (id == null) {
            return false;
        }
        api.update(id, toDto(updated));
        refresh();
        return true;
    }

    public boolean remove(Contact contact) {
        Long id = idOf(contact);
        if (id == null) {
            return false;
        }
        api.delete(id);
        refresh();
        return true;
    }

    public int removeByName(String name) {
        int removed = api.removeByName(name);
        refresh();
        return removed;
    }

    public int removeByPrefix(String prefix) {
        int removed = api.removeByPrefix(prefix);
        refresh();
        return removed;
    }

    public int generate(int count, long seed) {
        int generated = api.generate(count, seed);
        refresh();
        return generated;
    }

    public int clear() {
        int removed = api.clear();
        refresh();
        return removed;
    }

    public byte[] exportCsv() {
        return api.exportData("csv");
    }

    public byte[] exportData(String format, int version) {
        return api.exportData(format, version);
    }

    public ImportResultDto importCsv(byte[] data) {
        return importData("csv", data);
    }

    public ImportResultDto importData(String format, byte[] data) {
        ImportResultDto result = api.importData(format, data);
        refresh();
        return result;
    }

    private synchronized Long idOf(Contact contact) {
        return ids.get(contact);
    }

    private void reload(List<ContactDto> list) {
        Contact[] next = new Contact[list.size()];
        Map<Contact, Long> nextIds = new IdentityHashMap<>();
        Trie<Contact> nextIndex = new Trie<>(Contact.class, BY_NAME);
        for (int i = 0; i < list.size(); i++) {
            ContactDto dto = list.get(i);
            Contact contact = fromDto(dto);
            next[i] = contact;
            if (dto.id() != null) {
                nextIds.put(contact, dto.id());
            }
            nextIndex.insert(contact);
        }
        synchronized (this) {
            contacts = next;
            ids.clear();
            ids.putAll(nextIds);
            index = nextIndex;
        }
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private static Contact fromDto(ContactDto dto) {
        if (ContactType.EMERGENCY.getCsvId().equalsIgnoreCase(dto.type())) {
            return new EmergencyContact(
                    dto.name(), dto.phone(), value(dto.email()), value(dto.organization()),
                    value(dto.note()), value(dto.tags()));
        }
        return new CorporateContact(
                dto.name(), dto.phone(), value(dto.email()), value(dto.organization()),
                value(dto.position()), value(dto.internalNumber()),
                value(dto.note()), value(dto.tags()));
    }

    private static ContactDto toDto(Contact contact) {
        if (contact instanceof CorporateContact corporate) {
            return new ContactDto(
                    null,
                    ContactType.CORPORATE.getCsvId(),
                    corporate.getName(),
                    corporate.getPhone(),
                    corporate.getEmail(),
                    corporate.getOrganization(),
                    corporate.getPosition(),
                    corporate.getInternalNumber(),
                    corporate.getNote(),
                    corporate.getTags());
        }
        if (contact instanceof EmergencyContact emergency) {
            return new ContactDto(
                    null,
                    ContactType.EMERGENCY.getCsvId(),
                    emergency.getName(),
                    emergency.getPhone(),
                    emergency.getEmail(),
                    emergency.getOrganization(),
                    null,
                    null,
                    emergency.getNote(),
                    emergency.getTags());
        }
        throw new IllegalArgumentException("Неизвестный тип контакта: " + contact.getClass());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
