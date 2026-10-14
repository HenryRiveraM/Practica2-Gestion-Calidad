package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.dto.SurveyRequest;
import com.cocacola.eventintelligence.entity.Survey;
import com.cocacola.eventintelligence.service.SurveyService;
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
@RequestMapping("/api/surveys")
public class SurveyController {
    @org.springframework.beans.factory.annotation.Autowired
    protected com.cocacola.eventintelligence.security.AccessPolicy access;

    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    @GetMapping
    public List<Survey> findAll() {
        return service.findAll().stream().filter(access::canRead).toList();
    }

    @GetMapping("/{id}")
    public Survey findById(@PathVariable Long id) {
        var entity = service.findById(id);
        access.requireRead(entity);
        return entity;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Survey submit(@Valid @RequestBody SurveyRequest request) {
        access.requireOperate(request.eventId());
        access.requireParticipantAtEvent(request.eventId(), request.participantId());
        return service.submit(request);
    }

    @PutMapping("/{id}")
    public Survey update(@PathVariable Long id, @Valid @RequestBody Survey survey) {
        access.requireWrite(service.findById(id));
        access.requireWrite(survey);
        return service.update(id, survey);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        access.requireWrite(service.findById(id));
        service.delete(id);
    }
}
