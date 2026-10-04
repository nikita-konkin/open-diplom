-- Documents (ADR-0010): what the diploma and its supplement print besides
-- the registry of graduates, and the organization that issues them.

-- The organization as its documents print it. One row, «main».
CREATE TABLE organization (
    id VARCHAR(20) PRIMARY KEY,
    -- the full name, its lines as printed
    full_name VARCHAR(1000) NOT NULL,
    -- «г. Йошкар-Ола»
    locality VARCHAR(200) NOT NULL,
    head_last_name VARCHAR(100) NOT NULL,
    head_first_name VARCHAR(100) NOT NULL,
    head_middle_name VARCHAR(100) NOT NULL,
    updated_at VARCHAR(40) NOT NULL
);

-- A diploma with its supplement: one registration number and one date of
-- issue for both (Order No 670). A duplicate is another document of the same
-- graduate with a number of its own.
CREATE TABLE document (
    id VARCHAR(36) PRIMARY KEY,
    graduate_id VARCHAR(36) NOT NULL REFERENCES graduate (id),
    -- the document a duplicate replaces; empty for the original
    duplicate_of VARCHAR(36) REFERENCES document (id),
    -- a duplicate replaces the diploma, the supplement or both: 1 or 0
    diploma_duplicate INTEGER NOT NULL,
    supplement_duplicate INTEGER NOT NULL,
    -- unique in the registry; empty until given
    reg_number VARCHAR(40) UNIQUE,
    -- ГГГГ-ММ-ДД, empty until set
    issue_date VARCHAR(10) NOT NULL,
    -- «с отличием»: empty as the rule of item 27 says, 1 or 0 as the operator decided
    honors INTEGER,
    created_at VARCHAR(40) NOT NULL,
    updated_at VARCHAR(40) NOT NULL
);
