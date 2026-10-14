package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.entity.Campaign;
import com.cocacola.eventintelligence.service.CampaignService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController extends BaseCrudController<Campaign> {
    public CampaignController(CampaignService service) {
        super(service);
    }
}
