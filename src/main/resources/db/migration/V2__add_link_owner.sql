-- SHA-256 hex of the creating API key. Nullable: links created before auth existed have no owner.
ALTER TABLE links ADD COLUMN owner_key_fingerprint CHAR(64);
