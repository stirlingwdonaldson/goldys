-- S01: Native column types, nullable fields, defaults. Metadata only.
SELECT c.relname AS table_name,a.attnum AS ordinal,a.attname AS column_name,
       format_type(a.atttypid,a.atttypmod) AS native_type,NOT a.attnotnull AS nullable,
       pg_get_expr(d.adbin,d.adrelid) AS default_expression
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
WHERE n.nspname='public' AND c.relkind IN ('r','v','m') ORDER BY c.relname,a.attnum;
-- S02: Enforced keys/checks. Relationships in application code are separate.
SELECT c.relname AS table_name,con.conname,con.contype,
       pg_get_constraintdef(con.oid) AS definition
FROM pg_constraint con JOIN pg_class c ON c.oid=con.conrelid
JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'
ORDER BY c.relname,con.conname;
-- S03: Indexes and views.
SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY tablename,indexname;
SELECT schemaname,viewname,definition FROM pg_views WHERE schemaname='public';
-- S04: Size/estimates before deciding which scans are acceptable.
SELECT c.relname,c.reltuples::bigint AS estimated_rows,
       pg_total_relation_size(c.oid) AS total_bytes,s.n_live_tup,s.n_dead_tup
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
LEFT JOIN pg_stat_user_tables s ON s.relid=c.oid
WHERE n.nspname='public' AND c.relkind='r' ORDER BY c.relname;
-- S05: Applied migrations; no migration execution or mutation.
SELECT installed_rank,version,description,script,checksum,installed_on,success
FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT current_database(),current_user,current_schema(),version(),
       current_setting('search_path'),current_setting('transaction_read_only'),
       r.rolsuper,r.rolcreaterole,r.rolcreatedb
FROM pg_roles r WHERE r.rolname=current_user;
