package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.entity.Product;
import com.cocacola.eventintelligence.service.ProductService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController extends BaseCrudController<Product> {
    public ProductController(ProductService service) {
        super(service);
    }
}
