CREATE TABLE course (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
);

CREATE TABLE course_session (
    id         BIGSERIAL PRIMARY KEY,
    course_id  BIGINT       NOT NULL REFERENCES course (id),
    start_date DATE         NOT NULL,
    place      VARCHAR(100) NOT NULL,
    capacity   INT          NOT NULL CHECK (capacity > 0),
    status     VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
);

CREATE INDEX idx_course_session_course_id ON course_session (course_id);
