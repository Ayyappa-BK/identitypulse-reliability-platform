CREATE TABLE principals (
 subject varchar(200) PRIMARY KEY,
 enabled boolean NOT NULL DEFAULT false
);
-- No implicit enrollment and no enabled accounts in production migrations.
