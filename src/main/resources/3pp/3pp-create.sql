CREATE TABLE license (
  id       SERIAL NOT NULL PRIMARY KEY,
  lic_type VARCHAR(60),
  lic_url  VARCHAR(400)
);

CREATE TABLE software (
  id           SERIAL       NOT NULL PRIMARY KEY,
  shortname    VARCHAR(200) NOT NULL,
  version      VARCHAR(60),
  vendor       VARCHAR(60),
  created      TIMESTAMP WITH TIME ZONE DEFAULT now(),
  request_type VARCHAR(60),
  team_name    VARCHAR(100),
  owner_name   VARCHAR(100),
  fk_license   INTEGER REFERENCES license ON UPDATE CASCADE ON DELETE SET NULL
);