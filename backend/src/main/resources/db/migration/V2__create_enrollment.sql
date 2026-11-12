CREATE TABLE enrollment (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT      NOT NULL REFERENCES course_session (id),
    course_id   BIGINT      NOT NULL REFERENCES course (id),
    employee_id BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_enrollment_session_status ON enrollment (session_id, status);

-- D-01: 같은 과정에서 진행 중(확정·대기) 신청은 1인 1건. 동시에 두 번 눌러도 DB가 막는다
CREATE UNIQUE INDEX ux_enrollment_active_course
    ON enrollment (course_id, employee_id)
    WHERE status IN ('CONFIRMED', 'WAITING');
