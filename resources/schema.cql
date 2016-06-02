CREATE TABLE witan_workspace.events (
   key text,
   id uuid,
   event_id uuid,
   event_version text,
   creator uuid,
   origin text,
   received_at timestamp,
   original_payload text,
   PRIMARY KEY (id, received_at, key)
) WITH CLUSTERING ORDER BY (received_at ASC);

// -----------------------------------------------------

CREATE TABLE witan_workspace.workspaces_by_id (
   id uuid PRIMARY KEY,
   owner uuid,
   name text,
   created_at timestamp,
   last_updated timestamp
);

CREATE TABLE witan_workspace.workspaces_by_owner (
   id uuid,
   owner uuid PRIMARY KEY,
   name text,
   created_at timestamp,
   last_updated timestamp
);
