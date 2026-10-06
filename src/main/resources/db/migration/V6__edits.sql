-- Corrections the operator makes in the card of a graduate (ADR-0012). They
-- lie over what the files gave and are kept apart from it: the group loaded
-- again keeps them, and a file changed since a correction shows against it.
CREATE TABLE graduate_edit (
    id VARCHAR(36) PRIMARY KEY,
    graduate_id VARCHAR(36) NOT NULL REFERENCES graduate (id),
    -- a column of the graduate, «gek_protocol»; of a result, «grade», «credits» or «kind»
    field VARCHAR(40) NOT NULL,
    -- the element of a result as a subject link keeps it, all empty for a column
    element_index VARCHAR(40) NOT NULL,
    element_key VARCHAR(600) NOT NULL,
    alternative_key VARCHAR(600) NOT NULL,
    -- 1 for the course work of the element
    course_work INTEGER NOT NULL,
    -- the name of the result as the card showed it, for the journal
    printed VARCHAR(600) NOT NULL,
    value VARCHAR(1000) NOT NULL,
    -- what the files had when the correction was made
    original VARCHAR(1000) NOT NULL,
    edited_at VARCHAR(40) NOT NULL,
    UNIQUE (graduate_id, field, element_index, element_key, alternative_key, course_work)
);
