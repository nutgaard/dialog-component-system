DROP TYPE IF EXISTS actor_enum CASCADE;
DROP TABLE IF EXISTS dialog CASCADE;
DROP INDEX IF EXISTS dialog_id_idx CASCADE;
DROP INDEX IF EXISTS dialog_owner_idx CASCADE;
DROP INDEX IF EXISTS dialog_components_type_idx CASCADE;

CREATE TYPE actor_enum AS ENUM ('Employee', 'System', 'ExternalUser', 'ExternalOrganization');

CREATE TABLE dialog
(
    id         ksuid PRIMARY KEY NOT NULL,
    source     source NOT NULL ,
    owner      actor NOT NULL ,
    owner_type actor_enum NOT NULL ,
    created    TIMESTAMP DEFAULT NOW() NOT NULL,
    components component_array DEFAULT '[]'::jsonb NOT NULL
);
CREATE INDEX dialog_id_idx ON dialog (id);
CREATE INDEX dialog_owner_idx ON dialog (owner);
CREATE INDEX dialog_components_type_idx ON dialog
    USING gin (components jsonb_path_ops);

CREATE OR REPLACE FUNCTION notify_dialog_update()
    RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    if (tg_op = 'INSERT') then
        PERFORM pg_notify('dialog_channel', NEW.id::text);
    elsif (tg_op = 'UPDATE' AND OLD.components != NEW.components) then
        PERFORM pg_notify('dialog_channel', NEW.id::text);
    end if;
    RETURN null;
END;
$$;

CREATE OR REPLACE TRIGGER dialog_new
    AFTER INSERT OR UPDATE of components
    ON dialog
    FOR EACH ROW
    EXECUTE PROCEDURE notify_dialog_update();

DROP TABLE IF EXISTS message CASCADE;
DROP INDEX IF EXISTS message_id_idx CASCADE;
DROP INDEX IF EXISTS message_dialog_id_idx CASCADE;
DROP INDEX IF EXISTS message_components_type_idx CASCADE;

CREATE TABLE message
(
    id         ksuid PRIMARY KEY NOT NULL,
    dialog_id  ksuid             NOT NULL,
    actor      actor NOT NULL ,
    actor_type actor_enum NOT NULL ,
    created    TIMESTAMP DEFAULT NOW() NOT NULL,
    components component_array DEFAULT '[]'::jsonb NOT NULL,
    content    VARCHAR           NOT NULL,
    CONSTRAINT fk_dialog FOREIGN KEY (dialog_id) REFERENCES dialog (id)
);

CREATE INDEX message_id_idx ON message (id);
CREATE INDEX message_dialog_id_idx ON message (dialog_id);
CREATE INDEX message_components_type_idx ON message
    USING gin (components jsonb_path_ops);

CREATE OR REPLACE FUNCTION notify_message_update()
    RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    if (tg_op = 'INSERT') then
        PERFORM pg_notify('message_channel', NEW.id::text);
    elsif (tg_op = 'UPDATE' AND OLD.components != NEW.components) then
        PERFORM pg_notify('message_channel', NEW.id::text);
    end if;
    RETURN null;
END;
$$;

CREATE OR REPLACE TRIGGER message_new
    AFTER INSERT OR UPDATE of components
    ON message
    FOR EACH ROW
EXECUTE PROCEDURE notify_message_update();