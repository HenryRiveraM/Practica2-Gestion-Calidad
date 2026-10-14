package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Product;
import com.cocacola.eventintelligence.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class ProductService extends AbstractCrudService<Product> {
    public ProductService(ProductRepository repository) {
        super(repository, "Producto");
    }
}