package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.entity.Participant;
import com.cocacola.eventintelligence.repository.ParticipantRepository;
import org.springframework.stereotype.Service;

@Service
public class ParticipantService extends AbstractCrudService<Participant> {
    public ParticipantService(ParticipantRepository repository) {
        super(repository, "Participante");
    }
}