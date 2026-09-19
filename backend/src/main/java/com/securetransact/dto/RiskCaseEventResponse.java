package com.securetransact.dto;

import com.securetransact.model.RiskCaseEvent;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RiskCaseEventResponse {
    private String type;
    private String message;
    private String actorName;
    private LocalDateTime createdAt;

    public static RiskCaseEventResponse from(RiskCaseEvent event) {
        RiskCaseEventResponse response = new RiskCaseEventResponse();
        response.setType(event.getType().name());
        response.setMessage(event.getMessage());
        response.setCreatedAt(event.getCreatedAt());
        if (event.getActor() != null) response.setActorName(event.getActor().getFirstName() + " " + event.getActor().getLastName());
        return response;
    }
}
