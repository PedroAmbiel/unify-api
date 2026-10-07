ALTER TABLE post_comments ADD COLUMN media_oid OID;
ALTER TABLE post_comments ADD COLUMN image_description VARCHAR(240);
ALTER TABLE post_comments ADD CONSTRAINT chk_comment_description_requires_image
    CHECK (image_description IS NULL OR media_oid IS NOT NULL);

-- Also releases media when comments are removed through a parent-post cascade.
CREATE OR REPLACE FUNCTION delete_post_comment_media()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.media_oid IS NOT NULL THEN
        PERFORM lo_unlink(OLD.media_oid);
    END IF;
    RETURN OLD;
EXCEPTION WHEN undefined_object THEN
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_post_comments_delete_large_object
    BEFORE DELETE ON post_comments
    FOR EACH ROW EXECUTE FUNCTION delete_post_comment_media();
