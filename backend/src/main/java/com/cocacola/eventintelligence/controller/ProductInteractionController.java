package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.entity.ProductInteraction;
import com.cocacola.eventintelligence.service.ProductInteractionService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-interactions")
public class ProductInteractionController extends BaseCrudController<ProductInteraction> {
    public ProductInteractionController(ProductInteractionService service) {
        super(service);
    }
}
