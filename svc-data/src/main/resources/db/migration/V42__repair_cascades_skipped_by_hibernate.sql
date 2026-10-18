-- V42: Flyway did not run between the Boot 4 upgrade and the spring-boot-flyway fix, so
-- Hibernate (ddl-auto: update) created api_key and news_article_topic first. Their
-- foreign keys came out ON DELETE NO ACTION, and V33/V35 skip the table (IF NOT EXISTS),
-- so the cascades those migrations declare never reached the database. Recreate each
-- foreign key with ON DELETE CASCADE. Safe on a fresh database, where V33/V35 already
-- created the cascade: the constraint is dropped and recreated unchanged.
--
-- Scope: only migrations after V29 were skipped. Both databases (kauri, localhost) have V1-V29
-- in their history, so earlier cascades such as V19's news_article_ticker/tag are in place.
-- Proven against a schema-only copy of kauri's tables: both FKs go NO ACTION -> CASCADE.

DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT tc.table_name, tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
            ON kcu.constraint_name = tc.constraint_name AND kcu.table_name = tc.table_name
        WHERE tc.constraint_type = 'FOREIGN KEY'
          AND ((tc.table_name = 'api_key' AND kcu.column_name = 'owner_id')
            OR (tc.table_name = 'news_article_topic' AND kcu.column_name = 'article_id'))
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', fk.table_name, fk.constraint_name);
    END LOOP;
END $$;

ALTER TABLE api_key
    ADD CONSTRAINT fk_api_key_owner FOREIGN KEY (owner_id)
        REFERENCES system_user (id) ON DELETE CASCADE;

ALTER TABLE news_article_topic
    ADD CONSTRAINT fk_natp_article FOREIGN KEY (article_id)
        REFERENCES news_article (id) ON DELETE CASCADE;
