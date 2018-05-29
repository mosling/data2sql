CREATE TABLE state
(
  id        SERIAL      NOT NULL PRIMARY KEY,
  shortname VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE people
(
  id                SERIAL      NOT NULL PRIMARY KEY,
  people_cn         VARCHAR(60) NOT NULL UNIQUE,
  displayname       VARCHAR(200),
  givenname         VARCHAR(200),
  surename          VARCHAR(200),
  password          VARCHAR(200),
  email             VARCHAR(200),
  fk_state          INTEGER REFERENCES state ON UPDATE CASCADE ON DELETE SET NULL,
  oath_device       JSON,
  pwd_modify_ts     TIMESTAMP,
  pwd_expiration_ts TIMESTAMP,
  modify_ts         TIMESTAMP,
  create_ts         TIMESTAMP
);
COMMENT ON COLUMN people.password IS 'Length of a SHA1024 needs 256 4bit hexadecimal values and additional space for the algorithm {SHA1024}.';

CREATE TABLE scope
(
  id        SERIAL      NOT NULL PRIMARY KEY,
  shortname VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE response_type
(
  id        SERIAL      NOT NULL PRIMARY KEY,
  shortname VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE endpoint_method
(
  id        SERIAL      NOT NULL PRIMARY KEY,
  shortname VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE oauth2provider
(
  id                        SERIAL      NOT NULL PRIMARY KEY,
  oauth2provider_uid        VARCHAR(60) NOT NULL,
  shortname                 VARCHAR(60),
  description               VARCHAR(200),
  client_type               VARCHAR(60),
  default_max_age           INTEGER,
  default_max_age_enabled   BOOLEAN,
  public_key_location       VARCHAR(60),
  jwt_public_key            VARCHAR(2048),
  password                  VARCHAR(200),
  token_signed_response_alg VARCHAR(60),
  fk_state                  INTEGER REFERENCES state ON UPDATE CASCADE ON DELETE SET NULL,
  fk_endpoint_method        INTEGER REFERENCES endpoint_method ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE oauth2provider_scope
(
  id                SERIAL NOT NULL PRIMARY KEY,
  fk_oauth2provider INTEGER REFERENCES oauth2provider ON UPDATE CASCADE ON DELETE SET NULL,
  fk_scope          INTEGER REFERENCES scope ON UPDATE CASCADE ON DELETE SET NULL,
  default_scope     BOOLEAN
);

CREATE TABLE oauth2provider_response_type
(
  id                SERIAL NOT NULL PRIMARY KEY,
  fk_oauth2provider INTEGER REFERENCES oauth2provider ON UPDATE CASCADE ON DELETE SET NULL,
  fk_response_type  INTEGER REFERENCES response_type ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE oauth2provider_redirect
(
  id                SERIAL NOT NULL PRIMARY KEY,
  fk_oauth2provider INTEGER REFERENCES oauth2provider ON UPDATE CASCADE ON DELETE SET NULL,
  redirect_uri      VARCHAR(1024)
);

CREATE TABLE oauth2provider_version_control
(
  id                SERIAL NOT NULL PRIMARY KEY,
  fk_oauth2provider INTEGER REFERENCES oauth2provider ON UPDATE CASCADE ON DELETE SET NULL,
  path              VARCHAR(1024)
);

CREATE TABLE organization_type
(
  id        SERIAL      NOT NULL PRIMARY KEY,
  shortname VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE organization
(
  id                       SERIAL      NOT NULL PRIMARY KEY,
  organization_cn          VARCHAR(60) NOT NULL UNIQUE,
  organization_name        VARCHAR(200),
  password_history_size    INTEGER,
  password_min_entropy     INTEGER,
  password_days_expiration INTEGER,
  fk_organization_type     INTEGER REFERENCES organization_type ON UPDATE CASCADE ON DELETE SET NULL,
  created                  TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE organization_oauth2provider
(
  id                SERIAL NOT NULL PRIMARY KEY,
  fk_oauth2provider INTEGER REFERENCES oauth2provider ON UPDATE CASCADE ON DELETE SET NULL,
  fk_organization   INTEGER REFERENCES organization ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE pod
(
  id         SERIAL      NOT NULL PRIMARY KEY,
  pod_cn     VARCHAR(60) NOT NULL UNIQUE,
  gmt_offset SMALLINT,
  pod_name   VARCHAR(200)
);

CREATE TABLE instance_type
(
  id                  SERIAL      NOT NULL PRIMARY KEY,
  instance_type_cn    VARCHAR(60) NOT NULL UNIQUE,
  instance_type_descr VARCHAR(60)
);

CREATE TABLE realm
(
  id              SERIAL      NOT NULL PRIMARY KEY,
  realm_cn        VARCHAR(60) NOT NULL UNIQUE,
  realm_name      VARCHAR(200),
  fk_organization INTEGER REFERENCES organization ON UPDATE CASCADE ON DELETE SET NULL,
  customer_name   VARCHAR(60)
);

CREATE TABLE instance
(
  id               SERIAL      NOT NULL PRIMARY KEY,
  instance_cn      VARCHAR(60) NOT NULL UNIQUE,
  instance_descr   VARCHAR(200),
  fk_realm         INTEGER REFERENCES realm ON UPDATE CASCADE ON DELETE SET NULL,
  fk_instance_type INTEGER REFERENCES instance_type ON UPDATE CASCADE ON DELETE SET NULL,
  fk_pod           INTEGER REFERENCES pod ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE service_type
(
  id                 SERIAL NOT NULL PRIMARY KEY,
  service_type_cn    VARCHAR(60),
  service_type_descr VARCHAR(200)
);

CREATE TABLE role
(
  id               SERIAL NOT NULL PRIMARY KEY,
  role_cn          VARCHAR(60),
  role_descr       VARCHAR(60),
  internal_role    BOOLEAN,
  enum_name        VARCHAR(60),
  role_scope       VARCHAR(60),
  fk_service_type  INTEGER REFERENCES service_type ON UPDATE CASCADE ON DELETE SET NULL,
  plugin_classname VARCHAR(60)
);

CREATE TABLE permission
(
  id        SERIAL      NOT NULL PRIMARY KEY,
  shortname VARCHAR(60) NOT NULL
);

CREATE TABLE role_permission
(
  id            SERIAL NOT NULL PRIMARY KEY,
  fk_role       INTEGER REFERENCES role ON UPDATE CASCADE ON DELETE SET NULL,
  fk_permission INTEGER REFERENCES permission ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE role_argument
(
  id             SERIAL NOT NULL PRIMARY KEY,
  fk_role        INTEGER REFERENCES role ON UPDATE CASCADE ON DELETE SET NULL,
  argument_index INTEGER,
  argument       VARCHAR(200)
);

CREATE TABLE people_access
(
  id              SERIAL NOT NULL PRIMARY KEY,
  fk_people       INTEGER REFERENCES people ON UPDATE CASCADE ON DELETE SET NULL,
  fk_organization INTEGER REFERENCES organization ON UPDATE CASCADE ON DELETE SET NULL,
  fk_role         INTEGER REFERENCES role ON UPDATE CASCADE ON DELETE SET NULL,
  instance_filter VARCHAR,
  CONSTRAINT people_access_ak UNIQUE (fk_people, fk_organization, fk_role)
);

CREATE TABLE assertion
(
  id        SERIAL NOT NULL PRIMARY KEY,
  shortname VARCHAR(60)
);

CREATE TABLE service_provider
(
  id                  SERIAL NOT NULL PRIMARY KEY,
  service_provider_cn VARCHAR(60),
  entity_id           VARCHAR(200),
  spname              VARCHAR(200),
  spurl               VARCHAR(200),
  metadata            XML,
  entityconfig        XML,
  fk_service_type     INTEGER REFERENCES service_type ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE people_service_provider
(
  id                  SERIAL  NOT NULL PRIMARY KEY,
  fk_people           INTEGER NOT NULL REFERENCES people ON UPDATE CASCADE ON DELETE RESTRICT,
  fk_service_provider INTEGER NOT NULL REFERENCES service_provider ON UPDATE CASCADE ON DELETE RESTRICT,
  sp_name             VARCHAR(200),
  CONSTRAINT people_service_provider_ak UNIQUE (fk_people, fk_service_provider)
);

CREATE TABLE service_provider_assertion
(
  id                  SERIAL  NOT NULL PRIMARY KEY,
  fk_service_provider INTEGER NOT NULL REFERENCES service_provider ON UPDATE CASCADE ON DELETE RESTRICT,
  fk_assertion        INTEGER NOT NULL REFERENCES assertion ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE audit_log_type
(
  id        SERIAL NOT NULL PRIMARY KEY,
  shortname VARCHAR(60),
  descr     VARCHAR(200)
);

CREATE TABLE audit_log
(
  id                SERIAL NOT NULL PRIMARY KEY,
  logtime           TIMESTAMP,
  object_cn         VARCHAR(60),
  subject           VARCHAR(200),
  fk_audit_log_type INTEGER REFERENCES audit_log_type ON UPDATE CASCADE ON DELETE SET NULL,
  fk_people         INTEGER REFERENCES people ON UPDATE CASCADE ON DELETE SET NULL,
  uid               VARCHAR(60)
);

CREATE TABLE third_party_type
(
  id        SERIAL NOT NULL PRIMARY KEY,
  shortname VARCHAR(60),
  descr     VARCHAR(200)
);

CREATE TABLE third_party_organization
(
  id                  SERIAL NOT NULL PRIMARY KEY,
  key                 VARCHAR(200),
  fk_third_party_type INTEGER REFERENCES third_party_type ON UPDATE CASCADE ON DELETE SET NULL,
  fk_organization     INTEGER REFERENCES organization ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT ak_third_party_organization
  UNIQUE (key, fk_third_party_type, fk_organization)
);

CREATE TABLE migration
(
  id        SERIAL       NOT NULL PRIMARY KEY,
  shortname VARCHAR(100) NOT NULL UNIQUE,
  descr     VARCHAR(2048),
  creator   VARCHAR(60),
  created   TIMESTAMP
);

CREATE VIEW view_organization_instance AS
  SELECT
    instance.id     AS instance_id,
    instance.instance_cn,
    instance_type.instance_type_cn,
    realm.id        AS realm_id,
    realm.realm_cn,
    realm.realm_name,
    organization.id AS organization_id,
    organization.organization_cn
  FROM instance,
    instance_type,
    realm,
    organization
  WHERE ((instance.fk_instance_type = instance_type.id) AND (instance.fk_realm = realm.id) AND
         (realm.fk_organization = organization.id));

CREATE VIEW view_people_org_role AS
  SELECT
    row_number()
    OVER (
      ORDER BY pa.fk_people DESC ) AS id,
    pa.fk_people,
    pa.fk_organization,
    pa.fk_role,
    p.people_cn,
    p.displayname,
    p.fk_state,
    o.organization_name,
    r.role_cn,
    r.enum_name
  FROM people_access pa,
    people p,
    organization o,
    role r
  WHERE ((pa.fk_people = p.id) AND (pa.fk_organization = o.id) AND (pa.fk_role = r.id));

CREATE VIEW people_roles AS
  SELECT DISTINCT
    people.id AS people_id,
    role.id   AS role_id,
    role.role_cn
  FROM people,
    people_access,
    role
  WHERE ((people_access.fk_people = people.id) AND (people_access.fk_role = role.id));

CREATE VIEW view_organization_audit_log AS
  SELECT
    organization.id AS org_id,
    organization.organization_name,
    audit_log.id    AS audit_id,
    audit_log.fk_audit_log_type,
    audit_log.fk_people
  FROM organization,
    audit_log
  WHERE (((audit_log.object_cn) :: TEXT = (organization.organization_cn) :: TEXT) AND
         ((audit_log.subject) :: TEXT ~~ '%ou=organization%' :: TEXT));

CREATE VIEW view_people_audit_log AS
  SELECT
    people.id    AS people_id,
    people.displayname,
    audit_log.id AS audit_id,
    audit_log.fk_audit_log_type,
    audit_log.fk_people
  FROM people,
    audit_log
  WHERE (((audit_log.object_cn) :: TEXT = (people.people_cn) :: TEXT) AND
         ((audit_log.subject) :: TEXT ~~ '%ou=people%' :: TEXT));


