-- Blanks of strict accounting and the handing of documents (ADR-0013;
-- Order No 670, items 5, 29, 47-50). A blank is kept from the batch it came
-- in to the document printed on it, or to the reason it was spoiled.

-- What came from the typographer: blanks of one form and series.
CREATE TABLE blank_batch (
    id VARCHAR(36) PRIMARY KEY,
    -- the form of the blank: diploma-03, diploma-03-honors, supplement-03
    form VARCHAR(30) NOT NULL,
    -- six digits (Order No 645)
    series VARCHAR(6) NOT NULL,
    -- the numbers as the operator wrote them: «0000101–0000150»
    numbers VARCHAR(400) NOT NULL,
    quantity INTEGER NOT NULL,
    -- ГГГГ-ММ-ДД
    received VARCHAR(10) NOT NULL,
    -- the waybill or anything else the operator notes
    note VARCHAR(400) NOT NULL,
    created_at VARCHAR(40) NOT NULL
);

-- One blank. Its number has seven digits; each form is numbered apart.
CREATE TABLE blank (
    form VARCHAR(30) NOT NULL,
    series VARCHAR(6) NOT NULL,
    number VARCHAR(7) NOT NULL,
    batch_id VARCHAR(36) NOT NULL REFERENCES blank_batch (id),
    -- free, used or spoiled
    status VARCHAR(10) NOT NULL,
    -- the document printed on it; of a spoiled one, the document it was spoiled with, if any
    document_id VARCHAR(36) REFERENCES document (id),
    -- why it was spoiled
    reason VARCHAR(400) NOT NULL,
    changed_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (form, series, number)
);

CREATE INDEX blank_document ON blank (document_id);

-- How a document was handed (item 29): in person, by a power of attorney or
-- by post, with the number and date of the attorney or of the postal item.
CREATE TABLE document_issue (
    document_id VARCHAR(36) PRIMARY KEY REFERENCES document (id),
    -- ГГГГ-ММ-ДД
    handed VARCHAR(10) NOT NULL,
    -- person, attorney or post
    way VARCHAR(20) NOT NULL,
    details VARCHAR(400) NOT NULL,
    created_at VARCHAR(40) NOT NULL
);
