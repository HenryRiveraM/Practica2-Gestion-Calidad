package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
}