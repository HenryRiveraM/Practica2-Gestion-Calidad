package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Conversion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversionRepository extends JpaRepository<Conversion, Long> {
}