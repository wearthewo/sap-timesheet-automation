-- Queue execution fields remain mutable; the logical export and captured payload do not.
CREATE FUNCTION protect_sap_export_payload() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN
  RAISE EXCEPTION 'SAP payload snapshot must be retained' USING ERRCODE='23514';
 END IF;
 IF NEW.payload IS DISTINCT FROM OLD.payload
 OR NEW.export_id IS DISTINCT FROM OLD.export_id
 OR NEW.timesheet_id IS DISTINCT FROM OLD.timesheet_id THEN
  RAISE EXCEPTION 'SAP export payload and identity are immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER sap_payload_immutable BEFORE UPDATE OR DELETE ON sap_export_jobs
 FOR EACH ROW EXECUTE FUNCTION protect_sap_export_payload();