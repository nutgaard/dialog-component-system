DROP TYPE IF EXISTS entity_enum CASCADE;
DROP TYPE IF EXISTS queue_enum CASCADE;
DROP TABLE IF EXISTS kafka_queue CASCADE;
DROP INDEX IF EXISTS kafka_queue_id_idx CASCADE;
DROP INDEX IF EXISTS kafka_queue_status_idx CASCADE;
DROP INDEX IF EXISTS kafka_queue_component_type_idx CASCADE;
DROP FUNCTION IF EXISTS notify_kafka_queue_update CASCADE;
DROP TRIGGER IF EXISTS kafka_queue_status on kafka_queue CASCADE;

CREATE TYPE entity_enum AS ENUM ('Dialog', 'Message');
CREATE TYPE queue_enum AS ENUM ('Queued', 'Running', 'Ok', 'Failed');

CREATE TABLE kafka_queue
(
    id              BIGSERIAL PRIMARY KEY,
    entity_id       ksuid NOT NULL ,
    entity_type     entity_enum,
    component_type  VARCHAR(50),
    created         TIMESTAMP DEFAULT NOW() NOT NULL,
    updated         TIMESTAMP DEFAULT NOW() NOT NULL,
    failed_attempts INT        DEFAULT 0 NOT NULL,
    status          queue_enum DEFAULT 'Queued' NOT NULL ,
    message         JSONB                NOT NULL
);

CREATE INDEX kafka_queue_id_idx ON kafka_queue (id);
CREATE INDEX kafka_queue_status_idx ON kafka_queue (status);
CREATE INDEX kafka_queue_component_type_idx ON kafka_queue (component_type);

CREATE OR REPLACE FUNCTION notify_kafka_queue_update()
    RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    if (tg_op = 'INSERT') then
        PERFORM pg_notify('kafka_queue_channel', NEW.id::text);
    elsif (tg_op = 'UPDATE' AND OLD.status != NEW.status) then
        PERFORM pg_notify('kafka_queue_channel', NEW.id::text);
    end if;
    RETURN null;
END
$$;


CREATE TRIGGER kafka_queue_status
    AFTER INSERT OR UPDATE OF status
    ON kafka_queue
    FOR EACH ROW
EXECUTE PROCEDURE notify_kafka_queue_update();