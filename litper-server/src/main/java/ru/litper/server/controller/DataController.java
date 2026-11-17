package ru.litper.server.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.litper.persistence.serialize.SerializationException;
import ru.litper.server.dto.ExportedData;
import ru.litper.server.dto.ImportResponse;
import ru.litper.server.service.ContactApiService;

/**
 * REST-экспорт и импорт справочника в JSON или бинарном формате.
 */
@RestController
@RequestMapping("/api")
public class DataController {

    private final ContactApiService service;

    public DataController(ContactApiService service) {
        this.service = service;
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) Integer version) throws SerializationException {
        ExportedData data = service.export(format, version);
        return ResponseEntity.ok()
                .contentType(data.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(data.fileName())
                                .build()
                                .toString())
                .body(data.content());
    }

    @PostMapping("/import")
    public ImportResponse importData(
            @RequestParam(defaultValue = "json") String format,
            @RequestBody(required = false) byte[] body) throws SerializationException {
        return service.importData(body, format);
    }
}
