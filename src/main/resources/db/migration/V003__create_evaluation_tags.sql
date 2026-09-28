CREATE TABLE "sc-avaliacoes-experiencia".evaluation_tag (
    evaluation_id UUID NOT NULL REFERENCES "sc-avaliacoes-experiencia".experience_evaluation(id) ON DELETE CASCADE,
    tag_code VARCHAR(40) NOT NULL CHECK (tag_code ~ '^[A-Z0-9_]{2,40}$'),
    PRIMARY KEY (evaluation_id, tag_code)
);