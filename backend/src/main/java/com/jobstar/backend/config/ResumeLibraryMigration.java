package com.jobstar.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResumeLibraryMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public ResumeLibraryMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer legacyColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'resume_version' and column_name = 'application_id'
                """, Integer.class);
        if (legacyColumnCount == null || legacyColumnCount == 0) return;

        // Preserve existing application-attached resumes as account-level library records.
        jdbcTemplate.update("""
                update resume_version resume
                set user_id = application.user_id
                from application
                where resume.application_id = application.id and resume.user_id is null
                """);
        jdbcTemplate.execute("alter table resume_version alter column application_id drop not null");
        jdbcTemplate.update("""
                update application target
                set resume_version_id = (
                    select id from resume_version
                    where application_id = target.id
                    order by id desc limit 1
                )
                where target.resume_version_id is null
                """);
    }
}
