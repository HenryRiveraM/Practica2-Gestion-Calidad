package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}