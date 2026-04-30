-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Add embedding column (DeepSeek embedding dimension = 1024)
ALTER TABLE health_knowledge ADD COLUMN IF NOT EXISTS embedding vector(1024);

-- IVFFlat index for approximate nearest neighbor search
-- Build after data is populated for best results
CREATE INDEX IF NOT EXISTS idx_knowledge_embedding ON health_knowledge USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

-- Cosine similarity search function
CREATE OR REPLACE FUNCTION search_knowledge(query_embedding vector(1024), top_k integer)
RETURNS TABLE(id varchar, category varchar, title varchar, content text, similarity double precision)
LANGUAGE sql STABLE AS $$
    SELECT id, category, title, content, 1 - (embedding <=> query_embedding) AS similarity
    FROM health_knowledge
    WHERE embedding IS NOT NULL
    ORDER BY embedding <=> query_embedding
    LIMIT top_k;
$$;
