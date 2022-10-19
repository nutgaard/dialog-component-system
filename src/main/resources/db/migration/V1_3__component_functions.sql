CREATE OR REPLACE FUNCTION add_component_to_dialog(in_id ksuid, in_component component)
    RETURNS SETOF dialog
    LANGUAGE sql AS
$$
UPDATE dialog
SET components = components || in_component
WHERE id = in_id;

INSERT INTO kafka_queue(entity_id, entity_type, component_type, message)
VALUES (in_id, 'Dialog', in_component ->> 'type', in_component);

SELECT *
FROM dialog
WHERE id = in_id;
$$;

CREATE OR REPLACE FUNCTION add_component_to_message(in_id ksuid, in_component component)
    RETURNS SETOF message
    LANGUAGE sql AS
$$
UPDATE message
SET components = components || in_component
WHERE id = in_id;

INSERT INTO kafka_queue(entity_id, entity_type, component_type, message)
VALUES (in_id, 'Message', in_component ->> 'type', in_component);

SELECT *
FROM message
WHERE id = in_id;
$$;

CREATE OR REPLACE FUNCTION find_message_with_component_type(in_componenttype varchar)
    RETURNS SETOF message
    LANGUAGE sql AS
$$
SELECT *
from message
WHERE components @> ('[{ "type": "' || in_componenttype || '" }]')::jsonb;
$$;

CREATE OR REPLACE FUNCTION has_component_type(in_row message, in_componenttype varchar)
    RETURNS BOOLEAN
    LANGUAGE sql AS
$$
SELECT in_row.components @> ('[{ "type": "' || in_componenttype || '" }]')::jsonb
$$;

CREATE OR REPLACE FUNCTION has_component_type(in_row dialog, in_componenttype varchar)
    RETURNS BOOLEAN
    LANGUAGE sql AS
$$
SELECT in_row.components @> ('[{ "type": "' || in_componenttype || '" }]')::jsonb
$$;