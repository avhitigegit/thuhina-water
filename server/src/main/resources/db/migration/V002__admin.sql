-- M01 Administration (design 4.3, 6 Administration)
-- The users and settings tables exist since V001; this adds indexes for the Audit log tab filters
-- (user, action, record type – each newest first) and for the users list.

CREATE INDEX ix_audit_log_username_ts ON audit_log (username, ts DESC);
CREATE INDEX ix_audit_log_action_ts ON audit_log (action, ts DESC);
CREATE INDEX ix_audit_log_entity_ts ON audit_log (entity, ts DESC);

CREATE INDEX ix_app_user_full_name ON app_user (lower(full_name));
