CREATE SCHEMA {{db-prefix}}_data;
SET search_path TO {{db-prefix}}_data;

CREATE TABLE greeting (
  g_id       TEXT NOT NULL PRIMARY KEY,
  g_template TEXT NOT NULL
);
