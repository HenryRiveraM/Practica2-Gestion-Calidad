package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.EventProduct;
import com.cocacola.eventintelligence.repository.EventProductRepository;
import org.springframework.stereotype.Service;

@Service
public class EventProductService extends AbstractCrudService<EventProduct> {
    public EventProductService(EventProductRepository repository) {
        super(repository, "Producto de evento");
    }
}