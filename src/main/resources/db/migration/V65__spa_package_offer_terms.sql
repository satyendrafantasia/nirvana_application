ALTER TABLE spa_packages ADD COLUMN title VARCHAR(100), ADD COLUMN description VARCHAR(1500),
    ADD COLUMN validity_days INT, ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN published_once BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE spa_packages SET published_once=TRUE WHERE status='ACTIVE';
CREATE TABLE spa_package_service (
    package_id BIGINT NOT NULL, service_id BIGINT NOT NULL,
    PRIMARY KEY(package_id,service_id), FOREIGN KEY(package_id) REFERENCES spa_packages(id) ON DELETE CASCADE,
    FOREIGN KEY(service_id) REFERENCES service(id)
);
CREATE TABLE spa_subscription_service (
    subscription_id BIGINT NOT NULL, service_id BIGINT NOT NULL,
    PRIMARY KEY(subscription_id,service_id), FOREIGN KEY(subscription_id) REFERENCES user_spa_package_subscription(id),
    FOREIGN KEY(service_id) REFERENCES service(id)
);
