package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.ProductInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductInteractionRepository extends JpaRepository<ProductInteraction, Long> {
}