-- Printing on blanks (ADR-0011): the templates of the typographers and the
-- calibration of the printer.

-- A FastReport template (.fr3) of a document for a level of education. The
-- templates are not in the repository (ADR-0003): each university uploads
-- the ones that came with its blanks.
CREATE TABLE blank_template (
    -- «diploma» or «supplement»
    kind VARCHAR(20) NOT NULL,
    -- the middle of the code of a direction: 03 бакалавриат, 04 магистратура, 05 специалитет
    level VARCHAR(2) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content BLOB NOT NULL,
    uploaded_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (kind, level)
);

-- How far the printer shifts the page, millimetres to the right and down.
-- One row, «main».
CREATE TABLE printer (
    id VARCHAR(20) PRIMARY KEY,
    dx REAL NOT NULL,
    dy REAL NOT NULL,
    updated_at VARCHAR(40) NOT NULL
);
