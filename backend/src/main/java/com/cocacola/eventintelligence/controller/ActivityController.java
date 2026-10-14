package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.dto.ActivityRequest;
import com.cocacola.eventintelligence.entity.Activity;
import com.cocacola.eventintelligence.service.ActivityService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    @org.springframework.beans.factory.annotation.Autowired
    protected com.cocacola.eventintelligence.security.AccessPolicy access;

    private final ActivityService service;

    public ActivityController(ActivityService service) {
        this.service = service;
    }

    @GetMapping
    public List<Activity> findAll() {
        return service.findAll().stream().filter(access::canRead).toList();
    }

    @GetMapping("/{id}")
    public Activity findById(@PathVariable Long id) {
        var entity = service.findById(id);
        access.requireRead(entity);
        return entity;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Activity record(@Valid @RequestBody ActivityRequest request) {
        access.requireOperate(request.eventId());
        access.requireParticipantAtEvent(request.eventId(), request.participantId());
        return service.record(request);
    }

    @PutMapping("/{id}")
    public Activity update(@PathVariable Long id, @Valid @RequestBody Activity activity) {
        access.requireWrite(service.findById(id));
        access.requireWrite(activity);
        return service.update(id, activity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        access.requireWrite(service.findById(id));
        service.delete(id);
    }
}
