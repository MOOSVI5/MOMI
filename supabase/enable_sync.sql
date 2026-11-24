-- ============================================================================
-- Momi Full Supabase Sync & Storage Policies (PostgreSQL Valid Syntax)
-- ============================================================================
-- Run this once in Supabase Dashboard -> SQL Editor -> New Query -> Run.
-- Allows Android + Web clients authenticated via Firebase Google Sign-In to
-- read/write all Momi tables and upload photos/videos/audio to Storage buckets.

-- Ensure extra media URL columns exist on upgraded schemas
ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS audio_url text;
ALTER TABLE public.market_products ADD COLUMN IF NOT EXISTS extra_image_urls text[] NOT NULL DEFAULT ARRAY[]::text[];

-- Table RLS Policies for all 19 Momi tables
DROP POLICY IF EXISTS profiles_sync_write ON public.profiles;
CREATE POLICY profiles_sync_write ON public.profiles
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS posts_sync_write ON public.posts;
CREATE POLICY posts_sync_write ON public.posts
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS comments_sync_write ON public.comments;
CREATE POLICY comments_sync_write ON public.comments
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS stories_sync_write ON public.stories;
CREATE POLICY stories_sync_write ON public.stories
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS follows_sync_write ON public.follows;
CREATE POLICY follows_sync_write ON public.follows
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS user_interactions_sync_write ON public.user_interactions;
CREATE POLICY user_interactions_sync_write ON public.user_interactions
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS notifications_sync_write ON public.notifications;
CREATE POLICY notifications_sync_write ON public.notifications
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS threads_sync_write ON public.threads;
CREATE POLICY threads_sync_write ON public.threads
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS chat_messages_sync_write ON public.chat_messages;
CREATE POLICY chat_messages_sync_write ON public.chat_messages
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS live_streams_sync_write ON public.live_streams;
CREATE POLICY live_streams_sync_write ON public.live_streams
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS live_frames_sync_write ON public.live_frames;
CREATE POLICY live_frames_sync_write ON public.live_frames
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS live_comments_sync_write ON public.live_comments;
CREATE POLICY live_comments_sync_write ON public.live_comments
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS merchants_sync_write ON public.merchants;
CREATE POLICY merchants_sync_write ON public.merchants
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS products_sync_write ON public.market_products;
CREATE POLICY products_sync_write ON public.market_products
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS market_orders_sync_write ON public.market_orders;
CREATE POLICY market_orders_sync_write ON public.market_orders
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS content_reports_sync_write ON public.content_reports;
CREATE POLICY content_reports_sync_write ON public.content_reports
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS wallets_sync_write ON public.wallets;
CREATE POLICY wallets_sync_write ON public.wallets
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS credit_transactions_sync_write ON public.credit_transactions;
CREATE POLICY credit_transactions_sync_write ON public.credit_transactions
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS app_config_sync_write ON public.app_config;
CREATE POLICY app_config_sync_write ON public.app_config
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- Ensure public Storage buckets exist for images, avatars, videos & audio
INSERT INTO storage.buckets (id, name, public)
VALUES
  ('momi-media', 'momi-media', true),
  ('momi-avatars', 'momi-avatars', true),
  ('momi-videos', 'momi-videos', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- Storage Object Policies for Momi media buckets
DROP POLICY IF EXISTS momi_storage_select ON storage.objects;
CREATE POLICY momi_storage_select ON storage.objects
  FOR SELECT TO anon, authenticated
  USING (bucket_id IN ('momi-media', 'momi-avatars', 'momi-videos'));

DROP POLICY IF EXISTS momi_storage_insert ON storage.objects;
CREATE POLICY momi_storage_insert ON storage.objects
  FOR INSERT TO anon, authenticated
  WITH CHECK (bucket_id IN ('momi-media', 'momi-avatars', 'momi-videos'));

DROP POLICY IF EXISTS momi_storage_update ON storage.objects;
CREATE POLICY momi_storage_update ON storage.objects
  FOR UPDATE TO anon, authenticated
  USING (bucket_id IN ('momi-media', 'momi-avatars', 'momi-videos'))
  WITH CHECK (bucket_id IN ('momi-media', 'momi-avatars', 'momi-videos'));

DROP POLICY IF EXISTS momi_storage_delete ON storage.objects;
CREATE POLICY momi_storage_delete ON storage.objects
  FOR DELETE TO anon, authenticated
  USING (bucket_id IN ('momi-media', 'momi-avatars', 'momi-videos'));
