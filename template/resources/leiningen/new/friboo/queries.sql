-- name: get-all-greetings
SELECT g_id,
       g_template
  FROM {{db-prefix}}_data.greeting
 ORDER BY g_id ASC;

-- name: get-greeting
SELECT g_id,
       g_template
  FROM {{db-prefix}}_data.greeting
 WHERE g_id = :id;

-- name: create-or-update-greeting!
WITH greeting_update AS (
     UPDATE {{db-prefix}}_data.greeting
        SET g_template = :template
      WHERE g_id = :id
  RETURNING g_id
)
INSERT INTO {{db-prefix}}_data.greeting (
            g_id,
            g_template
            )
     SELECT :id,
            :template
      WHERE NOT EXISTS(SELECT 1 FROM greeting_update);

-- name: delete-greeting!
DELETE FROM {{db-prefix}}_data.greeting WHERE g_id = :id;
