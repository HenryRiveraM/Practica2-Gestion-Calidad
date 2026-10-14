package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
}