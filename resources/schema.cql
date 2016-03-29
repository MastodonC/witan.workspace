CREATE TABLE IF NOT EXISTS witan_workspace.events (
   key text,
   id uuid,
   version text,
   creator uuid,
   origin text,
   received_at timestamp,
   original_payload text,
   PRIMARY KEY ((key), received_at)
) WITH CLUSTERING ORDER BY (received_at ASC);
