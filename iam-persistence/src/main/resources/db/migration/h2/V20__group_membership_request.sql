CREATE TABLE iam_group_request (
  ID BIGINT IDENTITY NOT NULL,
  UUID VARCHAR(36) NOT NULL UNIQUE,
  ACCOUNT_ID BIGINT,
  GROUP_ID BIGINT,
  STATUS VARCHAR(50),
  NOTES CLOB,
  MOTIVATION CLOB,
  CREATIONTIME TIMESTAMP NOT NULL,
  LASTUPDATETIME TIMESTAMP,
  PRIMARY KEY (ID));

ALTER TABLE iam_group_request ADD CONSTRAINT FK_iam_group_request_account_id FOREIGN KEY (ACCOUNT_ID) REFERENCES iam_account (ID);
ALTER TABLE iam_group_request ADD CONSTRAINT FK_iam_group_request_group_id FOREIGN KEY (GROUP_ID) REFERENCES iam_group (ID);
