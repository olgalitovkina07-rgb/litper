package ru.litper.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.litper.model.CorporateContact;
import ru.litper.model.EmergencyContact;
import ru.litper.service.ContactService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST API: CRUD, фильтры, операции Trie и коды ошибок.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContactApiTest {

    private static final String CORPORATE_JSON = """
            {
              "type": "corporate",
              "name": "Иван Иванов",
              "phone": "+79990000000",
              "email": "ivan@example.com",
              "organization": "ООО Ромашка",
              "position": "Инженер",
              "internalNumber": "123",
              "note": "Основной контакт",
              "tags": "работа,важное"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContactService service;

    @BeforeEach
    void clearContacts() {
        service.clear();
    }

    @Test
    void createReturns201AndLocation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPORATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Иван Иванов"))
                .andExpect(jsonPath("$.type").value("corporate"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = body.get("id").asLong();
        assertTrue(result.getResponse().getHeader("Location").endsWith("/api/contacts/" + id));
    }

    @Test
    void findByIdAndFiltersWork() throws Exception {
        long corporateId = service.add(new CorporateContact(
                "Иван Иванов", "+79990000000", "ivan@example.com",
                "ООО Ромашка", "Инженер", "123"));
        service.add(new EmergencyContact(
                "Пожарная служба", "101", "", "МЧС"));

        mockMvc.perform(get("/api/contacts/{id}", corporateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(corporateId))
                .andExpect(jsonPath("$.organization").value("ООО Ромашка"));

        mockMvc.perform(get("/api/contacts")
                        .param("name", "иван")
                        .param("type", "corporate")
                        .param("organization", "ромашка"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Иван Иванов"));

        mockMvc.perform(get("/api/contacts").param("type", "emergency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Пожарная служба"));
    }

    @Test
    void putUpdatesEditableContactAndKeepsId() throws Exception {
        CorporateContact original = new CorporateContact(
                "Иван Иванов", "+79990000000", "ivan@example.com",
                "ООО Ромашка", "Инженер", "123");
        long id = service.add(original);

        String updated = """
                {
                  "type": "corporate",
                  "name": "Пётр Петров",
                  "phone": "+79990000009",
                  "email": "petr@example.com",
                  "organization": "ООО Ромашка",
                  "position": "Ведущий инженер",
                  "internalNumber": "456",
                  "note": "",
                  "tags": "работа"
                }
                """;

        mockMvc.perform(put("/api/contacts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Пётр Петров"))
                .andExpect(jsonPath("$.position").value("Ведущий инженер"));

        assertEquals("Пётр Петров", service.getById(id).getName());
    }

    @Test
    void putReadOnlyContactReturns422() throws Exception {
        long id = service.add(new EmergencyContact(
                "Пожарная служба", "101", "", "МЧС"));

        String body = """
                {
                  "type": "emergency",
                  "name": "Пожарная служба",
                  "phone": "102",
                  "email": "",
                  "organization": "МЧС"
                }
                """;

        mockMvc.perform(put("/api/contacts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Контакт нельзя изменить"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void deleteReturns204AndThen404() throws Exception {
        long id = service.add(new CorporateContact(
                "Иван Иванов", "+79990000000", "", "ООО", "Инженер", "123"));

        mockMvc.perform(delete("/api/contacts/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contacts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void duplicateContactReturns409() throws Exception {
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPORATE_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPORATE_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Конфликт данных"));
    }

    @Test
    void invalidRequestReturns400WithErrors() throws Exception {
        String body = """
                {
                  "type": "corporate",
                  "name": "",
                  "phone": ""
                }
                """;

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Ошибка валидации"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void domainValidationReturns400WithErrors() throws Exception {
        String body = """
                {
                  "type": "corporate",
                  "name": "Иван",
                  "phone": "+79990000000",
                  "position": "",
                  "internalNumber": "abc"
                }
                """;

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ошибка валидации"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void unknownTypeReturns400() throws Exception {
        String body = """
                {
                  "type": "unknown",
                  "name": "Иван",
                  "phone": "+79990000000"
                }
                """;

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный запрос"));
    }

    @Test
    void searchAndCountUseTrieOperations() throws Exception {
        service.add(new CorporateContact(
                "Иван Иванов", "+79990000000", "", "ООО", "Инженер", "123"));
        service.add(new CorporateContact(
                "Иван Петров", "+79990000001", "", "ООО", "Инженер", "124"));
        service.add(new CorporateContact(
                "Пётр Сидоров", "+79990000002", "", "ООО", "Инженер", "125"));

        mockMvc.perform(get("/api/contacts/search").param("prefix", "иван"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/contacts/search")
                        .param("prefix", "иван")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/contacts/count").param("prefix", "иван"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.prefix").value("иван"));
    }

    @Test
    void exportAndImportJsonRoundTrip() throws Exception {
        service.add(new CorporateContact(
                "Иван Иванов", "+79990000000", "ivan@example.com",
                "ООО Ромашка", "Инженер", "123"));

        MvcResult exported = mockMvc.perform(post("/api/export").param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("contacts.json")))
                .andReturn();

        service.clear();
        assertEquals(0, service.size());

        mockMvc.perform(post("/api/import")
                        .param("format", "json")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(exported.getResponse().getContentAsByteArray()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts").value(1))
                .andExpect(jsonPath("$.migrated").value(false))
                .andExpect(jsonPath("$.format").value("json"));

        assertEquals(1, service.size());
        assertEquals(1, service.findByName("Иван Иванов").length);
    }

    @Test
    void exportAndImportBinaryRoundTrip() throws Exception {
        service.add(new CorporateContact(
                "Иван Иванов", "+79990000000", "", "ООО", "Инженер", "123"));

        MvcResult exported = mockMvc.perform(post("/api/export").param("format", "bin"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn();

        service.clear();

        mockMvc.perform(post("/api/import")
                        .param("format", "bin")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(exported.getResponse().getContentAsByteArray()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contacts").value(1))
                .andExpect(jsonPath("$.format").value("bin"));

        assertEquals(1, service.size());
    }

    @Test
    void importOfOldVersionMigrates() throws Exception {
        String oldJson = """
                {
                  "schema_version": 1,
                  "contacts": [ {
                    "type": "corporate",
                    "name": "Старый контакт",
                    "phone": "+79990000000",
                    "email": "",
                    "organization": "ООО",
                    "position": "Инженер",
                    "internalNumber": "123"
                  } ]
                }
                """;

        mockMvc.perform(post("/api/import")
                        .param("format", "json")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(oldJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceVersion").value(1))
                .andExpect(jsonPath("$.migrated").value(true))
                .andExpect(jsonPath("$.contacts").value(1));

        assertEquals(1, service.size());
    }

    @Test
    void importOfFutureVersionReturns409() throws Exception {
        String futureJson = """
                {
                  "schema_version": 99,
                  "contacts": []
                }
                """;

        mockMvc.perform(post("/api/import")
                        .param("format", "json")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(futureJson))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fileVersion").value(99));
    }

    @Test
    void unknownImportFormatReturns400() throws Exception {
        mockMvc.perform(post("/api/import")
                        .param("format", "xml")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный запрос"));
    }

    @Test
    void damagedImportFileReturns400() throws Exception {
        mockMvc.perform(post("/api/import")
                        .param("format", "json")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Ошибка файла"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/contacts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Некорректный параметр"));
    }

    @Test
    void nonNumericSearchLimitReturns400() throws Exception {
        mockMvc.perform(get("/api/contacts/search")
                        .param("prefix", "иван")
                        .param("limit", "много"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Некорректный параметр"));
    }
}
