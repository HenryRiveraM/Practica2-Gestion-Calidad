package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.entity.Participant;
import com.cocacola.eventintelligence.service.ParticipantService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/participants")
public class ParticipantController extends BaseCrudController<Participant> {
    public ParticipantController(ParticipantService service) {
        super(service);
    }
}
