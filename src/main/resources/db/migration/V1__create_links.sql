CREATE TABLE links (
    code       VARCHAR(16)  PRIMARY KEY,
    long_url   TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
