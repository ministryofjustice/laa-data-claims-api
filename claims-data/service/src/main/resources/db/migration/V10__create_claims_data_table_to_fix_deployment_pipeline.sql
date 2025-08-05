-- TODO: NOTE: This is only here to fix integration tests which causes our deployment pipeline to fail.
--       Delete this table when the /api/v1/claims endpoint is removed.

CREATE TABLE claims
(
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(20)     NOT NULL,
    description VARCHAR(100)    NOT NULL
);

INSERT INTO CLAIMS(NAME, DESCRIPTION) VALUES('Claim One', 'This is a description of Claim One.');
INSERT INTO CLAIMS(NAME, DESCRIPTION) VALUES('Claim Two', 'This is a description of Claim Two.');
INSERT INTO CLAIMS(NAME, DESCRIPTION) VALUES('Claim Three', 'This is a description of Claim Three.');
INSERT INTO CLAIMS(NAME, DESCRIPTION) VALUES('Claim Four', 'This is a description of Claim Four.');
INSERT INTO CLAIMS(NAME, DESCRIPTION) VALUES('Claim Five', 'This is a description of Claim Five.');
