package ru.litper.client.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.litper.client.dto.ContactDto;
import ru.litper.client.dto.CountDto;
import ru.litper.client.dto.ImportResultDto;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpApiClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api", this::handle);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static final String CONTACT_OBJECT_JSON =
            "{\"id\":1,\"type\":\"corporate\",\"name\":\"Иван\",\"phone\":\"+7\","
                    + "\"email\":\"e\",\"organization\":\"o\",\"position\":\"p\","
                    + "\"internalNumber\":\"1\",\"note\":\"\",\"tags\":\"\"}";
    private static final String CONTACT_JSON = "[" + CONTACT_OBJECT_JSON + "]";

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String query = exchange.getRequestURI().getQuery();
        if ("/api/contacts/search".equals(path)) {
            respond(exchange, 200, CONTACT_JSON);
        } else if ("/api/contacts/count".equals(path)) {
            respond(exchange, 200, "{\"prefix\":\"Ив\",\"count\":1}");
        } else if ("/api/contacts/find".equals(path)) {
            respond(exchange, 200, CONTACT_JSON);
        } else if ("/api/contacts/generate".equals(path)) {
            respond(exchange, 200, "{\"generated\":3}");
        } else if ("/api/contacts/clear".equals(path)) {
            respond(exchange, 200, "{\"prefix\":\"\",\"count\":2}");
        } else if ("/api/contacts/by-name".equals(path)
                || "/api/contacts/by-prefix".equals(path)) {
            respond(exchange, 200, "{\"prefix\":\"Ив\",\"count\":1}");
        } else if ("/api/export".equals(path)) {
            if ("format=plain".equals(query)) {
                respond(exchange, 500, "plain error");
            } else {
                respond(exchange, 200, "csv-data");
            }
        } else if ("/api/import".equals(path)) {
            respond(exchange, 200, "{\"format\":\"csv\",\"sourceVersion\":3,"
                    + "\"currentVersion\":3,\"contacts\":1,\"migrated\":false,"
                    + "\"skippedLines\":2}");
        } else if ("/api/contacts".equals(path) && "POST".equals(method)) {
            readBody(exchange);
            respond(exchange, 201, CONTACT_OBJECT_JSON);
        } else if (path.matches("/api/contacts/\\d+") && "PUT".equals(method)) {
            readBody(exchange);
            respond(exchange, 200, CONTACT_OBJECT_JSON);
        } else if (path.matches("/api/contacts/\\d+") && "DELETE".equals(method)) {
            respondEmpty(exchange, 204);
        } else if ("/api/contacts".equals(path)) {
            respond(exchange, 200, CONTACT_JSON);
        } else {
            respond(exchange, 404,
                    "{\"title\":\"Не найдено\",\"detail\":\"Нет контакта\",\"status\":404}");
        }
    }

    private static void readBody(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
    }

    private static void respondEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Test
    void listsContactsFromServer() {
        List<ContactDto> contacts = new HttpApiClient(baseUrl).list(null, null, null);

        assertEquals(1, contacts.size());
        assertEquals("Иван", contacts.get(0).name());
        assertEquals("corporate", contacts.get(0).type());
    }

    @Test
    void readsCount() {
        CountDto count = new HttpApiClient(baseUrl).count("Ив");

        assertEquals("Ив", count.prefix());
        assertEquals(1, count.count());
    }

    @Test
    void mapsServerErrorToApiException() {
        ApiException error = assertThrows(ApiException.class,
                () -> new HttpApiClient(baseUrl).get(99));

        assertEquals(404, error.getStatus());
        assertTrue(error.getMessage().contains("Не найдено"));
        assertEquals("Нет контакта", error.getDetail());
    }

    @Test
    void reportsUnavailableServerAsNetworkError() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        ApiClient client = new HttpApiClient("http://localhost:" + freePort);

        ApiException error = assertThrows(ApiException.class,
                () -> client.list(null, null, null));

        assertEquals(0, error.getStatus());
    }

    @Test
    void findsContactsByName() {
        List<ContactDto> found = new HttpApiClient(baseUrl).findByName("Иван");

        assertEquals(1, found.size());
        assertEquals("Иван", found.get(0).name());
    }

    @Test
    void generatesAndClearsContacts() {
        ApiClient client = new HttpApiClient(baseUrl);

        assertEquals(3, client.generate(3, 42));
        assertEquals(2, client.clear());
    }

    @Test
    void removesContactsByNameAndPrefix() {
        ApiClient client = new HttpApiClient(baseUrl);

        assertEquals(1, client.removeByName("Иван"));
        assertEquals(1, client.removeByPrefix("Ив"));
    }

    @Test
    void exportsAndImportsCsv() {
        ApiClient client = new HttpApiClient(baseUrl);

        assertEquals("csv-data",
                new String(client.exportData("csv"), StandardCharsets.UTF_8));

        ImportResultDto result =
                client.importData("csv", "x".getBytes(StandardCharsets.UTF_8));
        assertEquals("csv", result.format());
        assertEquals(1, result.contacts());
        assertEquals(2, result.skippedLines());
        assertFalse(result.migrated());
    }

    @Test
    void createsContactThroughApi() {
        ContactDto created = new HttpApiClient(baseUrl).create(sampleContact());

        assertEquals(1L, created.id());
        assertEquals("Иван", created.name());
    }

    @Test
    void updatesContactThroughApi() {
        ContactDto updated = new HttpApiClient(baseUrl).update(1, sampleContact());

        assertEquals(1L, updated.id());
        assertEquals("p", updated.position());
    }

    @Test
    void deletesContactThroughApi() {
        ApiClient client = new HttpApiClient(baseUrl);

        assertDoesNotThrow(() -> client.delete(1));
    }

    @Test
    void searchesContactsByPrefix() {
        List<ContactDto> found = new HttpApiClient(baseUrl).search("Ив", 10);

        assertEquals(1, found.size());
        assertEquals("Иван", found.get(0).name());
    }

    @Test
    void keepsRawServerErrorWhenProblemDetailIsBroken() {
        ApiException error = assertThrows(ApiException.class,
                () -> new HttpApiClient(baseUrl).exportData("plain"));

        assertEquals(500, error.getStatus());
        assertEquals("plain error", error.getDetail());
        assertNotNull(error.getCause());
    }

    private static ContactDto sampleContact() {
        return new ContactDto(null, "corporate", "Иван", "+7", "e", "o",
                "Инженер", "1", "", "");
    }
}
