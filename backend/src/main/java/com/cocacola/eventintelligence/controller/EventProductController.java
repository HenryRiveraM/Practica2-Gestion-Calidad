package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.entity.EventProduct;
import com.cocacola.eventintelligence.service.EventProductService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-products")
public class EventProductController extends BaseCrudController<EventProduct> {
    public EventProductController(EventProductService service) {
        super(service);
    }
}
