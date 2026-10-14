package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.BaseEntity;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public abstract class AbstractCrudService<T extends BaseEntity> {
    private final JpaRepository<T, Long> repository;
    private final String resourceName;

    protected AbstractCrudService(JpaRepository<T, Long> repository, String resourceName) {
        this.repository = repository;
        this.resourceName = resourceName;
    }

    @Transactional(readOnly = true)
    public List<T> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public T findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(resourceName, id));
    }

    public T create(T entity) {
        entity.setId(null);
        validate(entity);
        return repository.save(entity);
    }

    public T update(Long id, T entity) {
        findById(id);
        entity.setId(id);
        validate(entity);
        return repository.save(entity);
    }

    public void delete(Long id) {
        repository.delete(findById(id));
    }

    protected void validate(T entity) {
    }
}