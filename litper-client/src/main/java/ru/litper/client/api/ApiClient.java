package ru.litper.client.api;

import ru.litper.client.dto.ContactDto;
import ru.litper.client.dto.CountDto;
import ru.litper.client.dto.ImportResultDto;

import java.util.List;

public interface ApiClient {

    List<ContactDto> list(String name, String organization, String type);

    ContactDto get(long id);

    ContactDto create(ContactDto contact);

    ContactDto update(long id, ContactDto contact);

    void delete(long id);

    List<ContactDto> search(String prefix, int limit);

    List<ContactDto> findByName(String name);

    CountDto count(String prefix);

    int generate(int count, long seed);

    int clear();

    int removeByName(String name);

    int removeByPrefix(String prefix);

    byte[] exportData(String format);

    byte[] exportData(String format, int version);

    ImportResultDto importData(String format, byte[] data);
}
