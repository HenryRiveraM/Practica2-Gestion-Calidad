package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.entity.Conversion;
import com.cocacola.eventintelligence.service.ConversionService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversions")
public class ConversionController extends BaseCrudController<Conversion> {
    public ConversionController(ConversionService service) {
        super(service);
    }
}
