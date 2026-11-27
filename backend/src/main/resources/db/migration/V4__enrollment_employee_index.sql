-- FR-07: 사원별 신청 내역 조회 (NFR-02)
CREATE INDEX idx_enrollment_employee ON enrollment (employee_id, id DESC);
