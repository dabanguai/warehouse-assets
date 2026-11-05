-- 其他出库保留原出库对象，并可关联到剧组跟进。
ALTER TABLE documents ADD COLUMN related_crew_id VARCHAR(100) NULL AFTER crew_id,
    ADD CONSTRAINT fk_document_related_crew FOREIGN KEY (related_crew_id) REFERENCES crews(id);
