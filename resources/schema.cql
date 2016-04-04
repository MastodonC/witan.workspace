CREATE TABLE witan_workspace.events (
   key text,
   id uuid,
   event_id uuid,
   event_version text,
   creator uuid,
   origin text,
   received_at timestamp,
   params text,
   original_payload text,
   PRIMARY KEY (id, received_at, key)
) WITH CLUSTERING ORDER BY (received_at ASC);
