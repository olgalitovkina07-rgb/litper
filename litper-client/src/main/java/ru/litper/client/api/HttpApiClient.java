package ru.litper.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.litper.client.dto.ContactDto;
import ru.litper.client.dto.CountDto;
import ru.litper.client.dto.GeneratedDto;
import ru.litper.client.dto.ImportResultDto;
import ru.litper.client.dto.ProblemDto;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class HttpApiClient implements ApiClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public HttpApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public List<ContactDto> list(String name, String organization, String type) {
        StringBuilder path = new StringBuilder("/api/contacts");
        String sep = "?";
        if (notBlank(name)) { path.append(sep).append("name=").append(enc(name)); sep = "&"; }
        if (notBlank(organization)) { path.append(sep).append("organization=").append(enc(organization)); sep = "&"; }
        if (notBlank(type)) { path.append(sep).append("type=").append(enc(type)); }
        HttpRequest req = request(path.toString()).GET().build();
        return sendJson(req, new TypeReference<List<ContactDto>>() {});
    }

    @Override
    public ContactDto get(long id) {
        HttpRequest req = request("/api/contacts/" + id).GET().build();
        return sendJson(req, ContactDto.class);
    }

    @Override
    public ContactDto create(ContactDto contact) {
        HttpRequest req = request("/api/contacts")
                .header("Content-Type", "application/json")
                .POST(jsonBody(contact))
                .build();
        return sendJson(req, ContactDto.class);
    }

    @Override
    public ContactDto update(long id, ContactDto contact) {
        HttpRequest req = request("/api/contacts/" + id)
                .header("Content-Type", "application/json")
                .PUT(jsonBody(contact))
                .build();
        return sendJson(req, ContactDto.class);
    }

    @Override
    public void delete(long id) {
        HttpRequest req = request("/api/contacts/" + id).DELETE().build();
        sendString(req);
    }

    @Override
    public List<ContactDto> search(String prefix, int limit) {
        String path = "/api/contacts/search?prefix=" + enc(prefix == null ? "" : prefix) + "&limit=" + limit;
        HttpRequest req = request(path).GET().build();
        return sendJson(req, new TypeReference<List<ContactDto>>() {});
    }

    @Override
    public List<ContactDto> findByName(String name) {
        String path = "/api/contacts/find?name=" + enc(name == null ? "" : name);
        HttpRequest req = request(path).GET().build();
        return sendJson(req, new TypeReference<List<ContactDto>>() {});
    }

    @Override
    public CountDto count(String prefix) {
        String path = "/api/contacts/count?prefix=" + enc(prefix == null ? "" : prefix);
        HttpRequest req = request(path).GET().build();
        return sendJson(req, CountDto.class);
    }

    @Override
    public int generate(int count, long seed) {
        String path = "/api/contacts/generate?count=" + count + "&seed=" + seed;
        HttpRequest req = request(path)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        GeneratedDto result = sendJson(req, GeneratedDto.class);
        return result == null ? 0 : result.generated();
    }

    @Override
    public int clear() {
        HttpRequest req = request("/api/contacts/clear")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        CountDto result = sendJson(req, CountDto.class);
        return result == null ? 0 : result.count();
    }

    @Override
    public int removeByName(String name) {
        String path = "/api/contacts/by-name?name=" + enc(name == null ? "" : name);
        HttpRequest req = request(path).DELETE().build();
        CountDto result = sendJson(req, CountDto.class);
        return result == null ? 0 : result.count();
    }

    @Override
    public int removeByPrefix(String prefix) {
        String path = "/api/contacts/by-prefix?prefix=" + enc(prefix == null ? "" : prefix);
        HttpRequest req = request(path).DELETE().build();
        CountDto result = sendJson(req, CountDto.class);
        return result == null ? 0 : result.count();
    }

    @Override
    public byte[] exportData(String format) {
        HttpRequest req = request("/api/export?format=" + enc(format))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return sendBytes(req);
    }

    @Override
    public byte[] exportData(String format, int version) {
        HttpRequest req = request("/api/export?format=" + enc(format) + "&version=" + version)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return sendBytes(req);
    }

    @Override
    public ImportResultDto importData(String format, byte[] data) {
        HttpRequest req = request("/api/import?format=" + enc(format))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(data == null ? new byte[0] : data))
                .build();
        return sendJson(req, ImportResultDto.class);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15));
    }

    private HttpRequest.BodyPublisher jsonBody(Object value) {
        try {
            return HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(value));
        } catch (IOException e) {
            throw new ApiException(0, "Ошибка клиента", "Не удалось сформировать запрос", e);
        }
    }

    private <T> T sendJson(HttpRequest req, Class<T> type) {
        HttpResponse<String> resp = sendString(req);
        if (resp.statusCode() == 204 || resp.body() == null || resp.body().isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(resp.body(), type);
        } catch (IOException e) {
            throw new ApiException(resp.statusCode(), "Ошибка ответа", "Некорректный JSON в ответе сервера", e);
        }
    }

    private <T> T sendJson(HttpRequest req, TypeReference<T> type) {
        HttpResponse<String> resp = sendString(req);
        if (resp.body() == null || resp.body().isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(resp.body(), type);
        } catch (IOException e) {
            throw new ApiException(resp.statusCode(), "Ошибка ответа", "Некорректный JSON в ответе сервера", e);
        }
    }

    private byte[] sendBytes(HttpRequest req) {
        try {
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 400) {
                throw toException(resp.statusCode(), new String(resp.body(), StandardCharsets.UTF_8));
            }
            return resp.body();
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(0, "Нет соединения", "Сервер недоступен: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Запрос прерван", "Запрос был прерван", e);
        }
    }

    private HttpResponse<String> sendString(HttpRequest req) {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 400) {
                throw toException(resp.statusCode(), resp.body());
            }
            return resp;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(0, "Нет соединения", "Сервер недоступен: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Запрос прерван", "Запрос был прерван", e);
        }
    }

    private ApiException toException(int status, String body) {
        if (body != null && !body.isBlank()) {
            try {
                ProblemDto problem = mapper.readValue(body, ProblemDto.class);
                String detail = problem.detail();
                if (problem.errors() != null && !problem.errors().isEmpty()) {
                    detail = String.join("\n", problem.errors());
                }
                String title = problem.title() != null ? problem.title() : "Ошибка";
                if (title != null && detail != null && title.equals(detail)) {
                    detail = null;
                }
                return new ApiException(status, title, detail);
            } catch (IOException ignored) {
            }
        }
        String title = switch (status) {
            case 400 -> "Неверный запрос";
            case 404 -> "Не найдено";
            case 409 -> "Конфликт";
            case 422 -> "Нельзя выполнить";
            case 500 -> "Ошибка сервера";
            default -> "Ошибка";
        };
        return new ApiException(status, title, body == null || body.isBlank() ? null : body);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
