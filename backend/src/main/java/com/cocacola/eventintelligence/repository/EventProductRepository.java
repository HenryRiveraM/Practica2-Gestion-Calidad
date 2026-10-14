package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.EventProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventProductRepository extends JpaRepository<EventProduct, Long> {
}