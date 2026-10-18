\pset tuples_only on
\pset format unaligned
SELECT format('SELECT %L || ''|'' || count(*) || ''|'' || md5(coalesce(string_agg(row_to_json(x)::text, '''' ORDER BY row_to_json(x)::text),'''')) FROM %I.%I x;',
 schemaname||'.'||tablename,schemaname,tablename)
FROM pg_tables WHERE schemaname='public' ORDER BY tablename
\gexec
