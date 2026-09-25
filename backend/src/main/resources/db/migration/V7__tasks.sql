CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    animal_id UUID REFERENCES animals(id) ON DELETE CASCADE,
    task_type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    assigned_to VARCHAR(120),
    notes TEXT,
    created_by VARCHAR(120),
    completed_at TIMESTAMPTZ,
    completed_by VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_task_status CHECK (status IN ('OPEN','COMPLETED','SKIPPED')),
    CONSTRAINT chk_task_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT'))
);

CREATE INDEX idx_tasks_status_due ON tasks(status,due_at);
CREATE INDEX idx_tasks_animal_due ON tasks(animal_id,due_at);
CREATE INDEX idx_tasks_assigned ON tasks(assigned_to);
