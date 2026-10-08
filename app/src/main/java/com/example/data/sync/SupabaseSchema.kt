package com.example.data.sync

object SupabaseSchema {

    val COMPLETE_SQL_SCRIPT: String = """
-- =====================================================================
-- QIIMO QUIZ — SUPABASE SQL SCHEMA & ROW LEVEL SECURITY (RLS) POLICIES
-- =====================================================================
-- Run this script in your Supabase project's SQL Editor (Dashboard > SQL Editor)
--
-- Features:
-- 1. Per-user data isolation via auth.users(id)
-- 2. Row Level Security (RLS) on all tables (items, attempts, item_stats, exam_results)
-- 3. Last-Write-Wins synchronization support via updated_at bigint timestamp
-- 4. Automatic cascading deletion when an auth user is removed
-- =====================================================================

-- 1. ITEMS TABLE
CREATE TABLE IF NOT EXISTS public.items (
    id BIGINT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
    name TEXT NOT NULL,
    system_name TEXT,
    cost NUMERIC DEFAULT 0.0,
    wholesale_price NUMERIC DEFAULT 0.0,
    price NUMERIC NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    PRIMARY KEY (user_id, id)
);

-- Ensure user_id column exists if table was created previously
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='items' AND column_name='user_id') THEN
        ALTER TABLE public.items ADD COLUMN user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid();
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_items_user_updated ON public.items(user_id, updated_at);

-- 2. ATTEMPTS TABLE
CREATE TABLE IF NOT EXISTS public.attempts (
    id BIGINT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
    item_id BIGINT NOT NULL,
    timestamp BIGINT NOT NULL,
    type TEXT NOT NULL,
    result TEXT NOT NULL,
    answer_given NUMERIC,
    response_time_ms BIGINT DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    PRIMARY KEY (user_id, id)
);

DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='attempts' AND column_name='user_id') THEN
        ALTER TABLE public.attempts ADD COLUMN user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid();
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_attempts_user_item ON public.attempts(user_id, item_id);
CREATE INDEX IF NOT EXISTS idx_attempts_user_timestamp ON public.attempts(user_id, timestamp);

-- 3. ITEM_STATS TABLE
CREATE TABLE IF NOT EXISTS public.item_stats (
    item_id BIGINT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
    correct_streak INTEGER DEFAULT 0,
    wrong_count INTEGER DEFAULT 0,
    skipped_count INTEGER DEFAULT 0,
    weakness_score NUMERIC DEFAULT 1.0,
    next_due_at BIGINT DEFAULT 0,
    status TEXT DEFAULT 'NEW',
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    PRIMARY KEY (user_id, item_id)
);

DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='item_stats' AND column_name='user_id') THEN
        ALTER TABLE public.item_stats ADD COLUMN user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid();
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_item_stats_user_due ON public.item_stats(user_id, next_due_at);

-- 4. EXAM_RESULTS TABLE
CREATE TABLE IF NOT EXISTS public.exam_results (
    id BIGINT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
    timestamp BIGINT NOT NULL,
    total_questions INTEGER NOT NULL,
    correct_count INTEGER NOT NULL,
    wrong_count INTEGER NOT NULL,
    skipped_count INTEGER NOT NULL,
    score_percentage INTEGER NOT NULL,
    average_time_ms BIGINT DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    PRIMARY KEY (user_id, id)
);

DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='exam_results' AND column_name='user_id') THEN
        ALTER TABLE public.exam_results ADD COLUMN user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid();
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_exam_results_user_timestamp ON public.exam_results(user_id, timestamp DESC);

-- =====================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- =====================================================================

-- Enable RLS on all 4 tables
ALTER TABLE public.items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.item_stats ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exam_results ENABLE ROW LEVEL SECURITY;

-- Drop any existing policies before recreating
DROP POLICY IF EXISTS "Users can manage their own items" ON public.items;
DROP POLICY IF EXISTS "Users can manage their own attempts" ON public.attempts;
DROP POLICY IF EXISTS "Users can manage their own item_stats" ON public.item_stats;
DROP POLICY IF EXISTS "Users can manage their own exam_results" ON public.exam_results;

-- Policy 1: ITEMS (Only authenticated owner can view, insert, update and delete)
CREATE POLICY "Users can manage their own items"
    ON public.items
    FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Policy 2: ATTEMPTS
CREATE POLICY "Users can manage their own attempts"
    ON public.attempts
    FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Policy 3: ITEM_STATS
CREATE POLICY "Users can manage their own item_stats"
    ON public.item_stats
    FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Policy 4: EXAM_RESULTS
CREATE POLICY "Users can manage their own exam_results"
    ON public.exam_results
    FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);
""".trimIndent()
}
