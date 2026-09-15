-- 'value' is a reserved word in H2, which prevents the table from being
-- created in the H2 test profile. Rename to 'blacklist_value'.
-- PostgreSQL updates the existing index automatically on column rename.
ALTER TABLE fraud_blacklist RENAME COLUMN value TO blacklist_value;