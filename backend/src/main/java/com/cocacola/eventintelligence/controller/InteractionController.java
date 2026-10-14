package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.entity.Interaction;
import com.cocacola.eventintelligence.service.InteractionService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController extends BaseCrudController<Interaction> {
    public InteractionController(InteractionService service) {
        super(service);
    }
}
