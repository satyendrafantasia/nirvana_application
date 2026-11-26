-- Front-desk staff: a Nirvana account the owner adds to one spa. They run the day's appointments and
-- take payments, but can't see the money reports, return or void money, or change the spa.
CREATE TABLE spa_desk_staff (
    spa_id   BIGINT      NOT NULL,
    user_id  BIGINT      NOT NULL,
    added_by BIGINT      NOT NULL,
    added_at DATETIME(6) NOT NULL,
    PRIMARY KEY (spa_id, user_id),
    INDEX ix_desk_staff_user (user_id),
    FOREIGN KEY (spa_id) REFERENCES spa (id),
    FOREIGN KEY (user_id) REFERENCES app_user (id),
    FOREIGN KEY (added_by) REFERENCES app_user (id)
);
