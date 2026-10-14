package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.entity.BaseEntity;
import com.cocacola.eventintelligence.service.AbstractCrudService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

public abstract class BaseCrudController<T extends BaseEntity> {
    @org.springframework.beans.factory.annotation.Autowired
    protected com.cocacola.eventintelligence.security.AccessPolicy access;

    protected final AbstractCrudService<T> service;

    protected BaseCrudController(AbstractCrudService<T> service) {
        this.service = service;
    }

    @GetMapping
    public List<T> findAll() {
        return service.findAll().stream().filter(access::canRead).toList();
    }

    @GetMapping("/{id}")
    public T findById(@PathVariable Long id) {
        var entity = service.findById(id);
        access.requireRead(entity);
        return entity;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public T create(@Valid @RequestBody T entity) {
        access.requireWrite(entity);
        return service.create(entity);
    }

    @PutMapping("/{id}")
    public T update(@PathVariable Long id, @Valid @RequestBody T entity) {
        access.requireWrite(service.findById(id));
        access.requireWrite(entity);
        return service.update(id, entity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        access.requireWrite(service.findById(id));
        service.delete(id);
    }
}
