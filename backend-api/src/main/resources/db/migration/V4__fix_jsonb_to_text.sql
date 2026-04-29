alter table if exists health_report_summaries
    alter column evidence type text using evidence::text,
    alter column advice type text using advice::text;

alter table if exists chat_messages
    alter column evidence type text using evidence::text,
    alter column advice type text using advice::text;
