package ru.litper.server.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.litper.server.dto.ContactFilter;
import ru.litper.server.dto.ContactRequest;
import ru.litper.server.dto.ContactResponse;
import ru.litper.server.dto.CountResponse;
import ru.litper.server.dto.GeneratedResponse;
import ru.litper.server.service.ContactApiService;

import java.net.URI;
import java.util.List;

/**
 * REST API справочника контактов.
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactApiService service;

    public ContactController(ContactApiService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContactResponse> findAll(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String organization,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String tags) {
        return service.findAll(new ContactFilter(
                name, organization, type, phone, email, note, tags));
    }

    @GetMapping("/{id}")
    public ContactResponse findById(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<ContactResponse> create(@Valid @RequestBody ContactRequest request) {
        ContactResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ContactResponse update(@PathVariable long id,
                                  @Valid @RequestBody ContactRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public List<ContactResponse> search(
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam(defaultValue = "50") int limit) {
        return service.search(prefix, limit);
    }

    @GetMapping("/count")
    public CountResponse count(@RequestParam(defaultValue = "") String prefix) {
        return service.count(prefix);
    }

    @GetMapping("/find")
    public List<ContactResponse> findByName(@RequestParam(defaultValue = "") String name) {
        return service.findByName(name);
    }

    @PostMapping("/generate")
    public GeneratedResponse generate(
            @RequestParam(defaultValue = "1000") int count,
            @RequestParam(defaultValue = "42") long seed) {
        return new GeneratedResponse(service.generate(count, seed));
    }

    @PostMapping("/clear")
    public CountResponse clear() {
        return new CountResponse("", service.clear());
    }

    @DeleteMapping("/by-name")
    public CountResponse deleteByName(@RequestParam(defaultValue = "") String name) {
        return new CountResponse(name, service.removeByName(name));
    }

    @DeleteMapping("/by-prefix")
    public CountResponse deleteByPrefix(@RequestParam(defaultValue = "") String prefix) {
        return new CountResponse(prefix, service.removeByPrefix(prefix));
    }
}
