CREATE TABLE generated_service_day (
    service_id BIGINT NOT NULL,
    local_date DATE NOT NULL,
    PRIMARY KEY (service_id, local_date),
    FOREIGN KEY (service_id) REFERENCES service(id) ON DELETE CASCADE
);
ALTER TABLE slot ADD COLUMN therapist_id BIGINT NULL,
    ADD CONSTRAINT fk_slot_therapist FOREIGN KEY (therapist_id) REFERENCES therapist(id);
