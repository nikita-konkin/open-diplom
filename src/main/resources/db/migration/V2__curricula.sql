-- Curricula kept by editions (ADR-0008). A program is a direction and a
-- profile, a curriculum is a program for a study form and an admission year.
-- A plan loaded again with changes, or edited, becomes a new edition of its
-- curriculum. Nothing saved is overwritten, so a graduation can keep the
-- edition it was checked against.

CREATE TABLE program (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    profile_key VARCHAR(400) NOT NULL,
    direction VARCHAR(400) NOT NULL,
    profile VARCHAR(400) NOT NULL,
    qualification VARCHAR(100) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    UNIQUE (code, profile_key)
);

CREATE TABLE curriculum (
    id VARCHAR(36) PRIMARY KEY,
    program_id VARCHAR(36) NOT NULL REFERENCES program (id),
    study_form VARCHAR(40) NOT NULL,
    admission_year INTEGER NOT NULL,
    edition INTEGER NOT NULL,
    study_term VARCHAR(40) NOT NULL,
    -- pdf, xlsx or manual
    source VARCHAR(20) NOT NULL,
    source_file VARCHAR(255),
    based_on VARCHAR(36) REFERENCES curriculum (id),
    errors INTEGER NOT NULL,
    -- SHA-256 of the title and the rows: a plan loaded again unchanged is recognised
    fingerprint VARCHAR(64) NOT NULL,
    note VARCHAR(2000) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    UNIQUE (program_id, study_form, admission_year, edition)
);

-- Rows as the plan has them. Parents, sections and printed names are derived
-- by the program each time, so an edition does not depend on how an older
-- version of the program derived them.
CREATE TABLE curriculum_item (
    curriculum_id VARCHAR(36) NOT NULL REFERENCES curriculum (id),
    position INTEGER NOT NULL,
    item_index VARCHAR(40) NOT NULL,
    name VARCHAR(600) NOT NULL,
    exams VARCHAR(40) NOT NULL,
    tests VARCHAR(40) NOT NULL,
    graded_tests VARCHAR(40) NOT NULL,
    course_projects VARCHAR(40) NOT NULL,
    course_works VARCHAR(40) NOT NULL,
    credits NUMERIC(10, 2),
    credits_exams NUMERIC(10, 2),
    credits_classes NUMERIC(10, 2),
    hours NUMERIC(10, 2),
    hours_exams NUMERIC(10, 2),
    hours_classes NUMERIC(10, 2),
    hours_contact NUMERIC(10, 2),
    hours_self NUMERIC(10, 2),
    PRIMARY KEY (curriculum_id, position)
);

-- Who did what is not known until sign-in comes, when and what is.
CREATE TABLE audit (
    id VARCHAR(36) PRIMARY KEY,
    at VARCHAR(40) NOT NULL,
    subject VARCHAR(40) NOT NULL,
    subject_id VARCHAR(36) NOT NULL,
    action VARCHAR(40) NOT NULL,
    details VARCHAR(4000) NOT NULL
);
