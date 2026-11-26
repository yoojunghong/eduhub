CREATE TABLE employee (
    id            BIGSERIAL PRIMARY KEY,
    employee_no   VARCHAR(20)  NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    department    VARCHAR(50),
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL
);
CREATE UNIQUE INDEX ux_employee_no ON employee (employee_no);
