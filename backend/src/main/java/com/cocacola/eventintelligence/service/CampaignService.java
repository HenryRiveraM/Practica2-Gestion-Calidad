package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Campaign;
import com.cocacola.eventintelligence.repository.CampaignRepository;
import org.springframework.stereotype.Service;

@Service
public class CampaignService extends AbstractCrudService<Campaign> {
    public CampaignService(CampaignRepository repository) {
        super(repository, "Campaña");
    }
}