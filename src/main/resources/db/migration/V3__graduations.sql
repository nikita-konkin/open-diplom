-- Graduations (ADR-0004, ADR-0009): a group of one curriculum finishing
-- together. The loaded files wait in the staging zone while the operator
-- matches students and subjects; confirmed, the graduates and their results
-- are written to the registry and the files are deleted.

CREATE TABLE graduation (
    id VARCHAR(36) PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    -- staging or registered
    status VARCHAR(20) NOT NULL,
    study_form VARCHAR(40) NOT NULL,
    admission_year INTEGER,
    -- the edition the graduation is checked against
    curriculum_id VARCHAR(36) REFERENCES curriculum (id),
    statement_file VARCHAR(255) NOT NULL,
    info_file VARCHAR(255) NOT NULL,
    gek_chairman VARCHAR(400) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    updated_at VARCHAR(40) NOT NULL
);

-- What the operator decided while matching: a row of the information file,
-- a sheet or a subject, and where it goes. On confirmation the subjects become
-- links of the program; the choices about students stay with the registered
-- graduation and come along when the group is loaded again.
CREATE TABLE staging_choice (
    graduation_id VARCHAR(36) NOT NULL REFERENCES graduation (id),
    item VARCHAR(700) NOT NULL,
    choice VARCHAR(700) NOT NULL,
    PRIMARY KEY (graduation_id, item)
);

-- Statement subjects the operator linked to plan elements, per program: the
-- same group loaded again, or the group of the next year, is matched without
-- asking. An element is found again by its name, and by its index among
-- equally named ones, so a link outlives the edition it was made on.
CREATE TABLE subject_link (
    program_id VARCHAR(36) NOT NULL REFERENCES program (id),
    -- kind and name key: «дисциплина:математика»
    subject_key VARCHAR(700) NOT NULL,
    -- all three empty: the subject does not go to the supplement
    element_index VARCHAR(40) NOT NULL,
    element_key VARCHAR(600) NOT NULL,
    alternative_key VARCHAR(600) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (program_id, subject_key)
);

CREATE TABLE graduate (
    id VARCHAR(36) PRIMARY KEY,
    graduation_id VARCHAR(36) NOT NULL REFERENCES graduation (id),
    -- the order of the information file
    position INTEGER NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100) NOT NULL,
    birth_date VARCHAR(10) NOT NULL,
    previous_document VARCHAR(400) NOT NULL,
    previous_year VARCHAR(10) NOT NULL,
    gek_date VARCHAR(10) NOT NULL,
    gek_protocol VARCHAR(100) NOT NULL,
    thesis_topic VARCHAR(1000) NOT NULL,
    thesis_grade INTEGER,
    state_exam_grade INTEGER,
    statement_name VARCHAR(255) NOT NULL,
    student_number VARCHAR(40) NOT NULL,
    -- problems found reading the information file, one per line
    notes VARCHAR(4000) NOT NULL
);

-- A graded element of a graduate. The element is a position in the edition
-- of the graduation; the printed name and the credits are kept as written to
-- the supplement.
CREATE TABLE result (
    graduate_id VARCHAR(36) NOT NULL REFERENCES graduate (id),
    element INTEGER NOT NULL,
    -- дисциплина, практика, факультатив or курсовая
    kind VARCHAR(20) NOT NULL,
    printed VARCHAR(600) NOT NULL,
    grade INTEGER,
    grade_text VARCHAR(40) NOT NULL,
    credits NUMERIC(10, 2),
    PRIMARY KEY (graduate_id, element, kind)
);
