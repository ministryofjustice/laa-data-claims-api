CREATE TABLE claims
(
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(20)     NOT NULL,
    description VARCHAR(100)    NOT NULL
);