package com.shelter.api.task;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskDue(UUID id, UUID animalId, String animalName, String taskType, String title,
                      OffsetDateTime dueAt, String status, String priority, String assignedTo, String notes) {}
