-- ============================================================================
-- Momi Full Supabase Sync & Storage Policies (PostgreSQL Valid Syntax)
-- ============================================================================
-- Run this once in Supabase Dashboard -> SQL Editor -> New Query -> Run.
-- Allows Android + Web clients to read/write all Momi tables, Pages, Groups,
-- Showcase Starters, In-App Announcements, and upload media to Storage buckets.

-- 1. Ensure extra media, email uniqueness, interactions, community & announcement columns exist on upgraded schemas
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS email text NOT NULL DEFAULT '';
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS phone text NOT NULL DEFAULT '';
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_email_lower
  ON public.profiles(lower(email))
  WHERE email <> '';
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_phone_e164
  ON public.profiles(phone)
  WHERE phone <> '';

ALTER TABLE public.posts DROP CONSTRAINT IF EXISTS posts_video_duration_ms_check;
ALTER TABLE public.posts ADD CONSTRAINT posts_video_duration_ms_check
  CHECK (video_duration_ms BETWEEN 0 AND 4800000);

ALTER TABLE public.stories DROP CONSTRAINT IF EXISTS stories_video_duration_ms_check;
ALTER TABLE public.stories ADD CONSTRAINT stories_video_duration_ms_check
  CHECK (video_duration_ms BETWEEN 0 AND 4800000);

ALTER TABLE public.market_products DROP CONSTRAINT IF EXISTS market_products_video_duration_ms_check;
ALTER TABLE public.market_products ADD CONSTRAINT market_products_video_duration_ms_check
  CHECK (video_duration_ms BETWEEN 0 AND 4800000);

ALTER TABLE public.user_interactions DROP CONSTRAINT IF EXISTS user_interactions_target_type_check;
ALTER TABLE public.user_interactions ADD CONSTRAINT user_interactions_target_type_check
  CHECK (target_type IN ('LIKED_POST', 'VIEWED_POST', 'BOOKMARK', 'SEEN_STORY', 'LIKED_COMMENT'));

ALTER TABLE public.stories ADD COLUMN IF NOT EXISTS audio_url text;
ALTER TABLE public.market_products ADD COLUMN IF NOT EXISTS extra_image_urls text[] NOT NULL DEFAULT ARRAY[]::text[];

ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS page_id text;
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS group_id text;
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS community_name text NOT NULL DEFAULT '';

ALTER TABLE public.app_config ADD COLUMN IF NOT EXISTS announcement_id text NOT NULL DEFAULT '';
ALTER TABLE public.app_config ADD COLUMN IF NOT EXISTS announcement_title text NOT NULL DEFAULT '';
ALTER TABLE public.app_config ADD COLUMN IF NOT EXISTS announcement_body text NOT NULL DEFAULT '';
ALTER TABLE public.app_config ADD COLUMN IF NOT EXISTS latest_version_name text NOT NULL DEFAULT '2.3';
ALTER TABLE public.app_config ADD COLUMN IF NOT EXISTS release_notes text NOT NULL DEFAULT '';

INSERT INTO public.app_config (id, latest_version_name)
VALUES ('monetization', '2.3')
ON CONFLICT (id) DO UPDATE
SET latest_version_name = '2.3'
WHERE coalesce(public.app_config.latest_version_name, '') IN ('', '1.0', '2.0', '2.1', '2.2');

-- 2. Pages, Page Follows, Groups, Group Members & Showcase Starters Tables
CREATE TABLE IF NOT EXISTS public.pages (
  id text PRIMARY KEY,
  owner_uid text NOT NULL,
  owner_username text NOT NULL DEFAULT 'momi.creator',
  name text NOT NULL,
  handle text NOT NULL DEFAULT '',
  category text NOT NULL DEFAULT 'Creator',
  bio text NOT NULL DEFAULT '',
  cover_key text NOT NULL DEFAULT 'img_post_coastal',
  avatar_url text,
  is_verified boolean NOT NULL DEFAULT false,
  followers_count bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.page_follows (
  id text PRIMARY KEY,
  page_id text NOT NULL REFERENCES public.pages(id) ON DELETE CASCADE,
  user_id text NOT NULL,
  username text NOT NULL DEFAULT 'momi.user',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.groups (
  id text PRIMARY KEY,
  owner_uid text NOT NULL,
  owner_username text NOT NULL DEFAULT 'momi.creator',
  name text NOT NULL,
  category text NOT NULL DEFAULT 'Community',
  description text NOT NULL DEFAULT '',
  privacy text NOT NULL DEFAULT 'PUBLIC',
  cover_key text NOT NULL DEFAULT 'img_post_architecture',
  members_count bigint NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.group_members (
  id text PRIMARY KEY,
  group_id text NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
  user_id text NOT NULL,
  username text NOT NULL DEFAULT 'momi.user',
  role text NOT NULL DEFAULT 'MEMBER',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.showcase_accounts (
  username text PRIMARY KEY,
  display_name text NOT NULL,
  avatar_key text NOT NULL DEFAULT 'avatar_elena',
  avatar_url text,
  bio text NOT NULL DEFAULT '',
  category text NOT NULL DEFAULT 'Creator',
  location text NOT NULL DEFAULT '',
  is_verified boolean NOT NULL DEFAULT true,
  is_authorized boolean NOT NULL DEFAULT false,
  updated_by_email text NOT NULL DEFAULT '',
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.showcase_accounts ADD COLUMN IF NOT EXISTS avatar_url text;
ALTER TABLE public.showcase_accounts ADD COLUMN IF NOT EXISTS is_verified boolean NOT NULL DEFAULT true;

ALTER TABLE public.pages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.page_follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.showcase_accounts ENABLE ROW LEVEL SECURITY;

-- 3. Table RLS Policies for all Momi tables
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

DROP POLICY IF EXISTS pages_sync_write ON public.pages;
CREATE POLICY pages_sync_write ON public.pages
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS page_follows_sync_write ON public.page_follows;
CREATE POLICY page_follows_sync_write ON public.page_follows
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS groups_sync_write ON public.groups;
CREATE POLICY groups_sync_write ON public.groups
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS group_members_sync_write ON public.group_members;
CREATE POLICY group_members_sync_write ON public.group_members
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS showcase_accounts_sync_write ON public.showcase_accounts;
CREATE POLICY showcase_accounts_sync_write ON public.showcase_accounts
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 4. Ensure public Storage buckets exist for images, avatars, videos & audio
INSERT INTO storage.buckets (id, name, public, file_size_limit)
VALUES
  ('momi-media', 'momi-media', true, 52428800),
  ('momi-avatars', 'momi-avatars', true, 20971520),
  ('momi-videos', 'momi-videos', true, 2147483648)
ON CONFLICT (id) DO UPDATE
SET public = true,
    file_size_limit = EXCLUDED.file_size_limit;

-- 5. Storage Object Policies for Momi media buckets
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

-- 6. Enable Supabase Realtime CDC WebSocket Broadcasts for All Momi Tables
DO $$
DECLARE
  tbl text;
  momi_tables text[] := ARRAY[
    'profiles',
    'posts',
    'comments',
    'stories',
    'follows',
    'user_interactions',
    'notifications',
    'threads',
    'chat_messages',
    'live_streams',
    'live_frames',
    'live_comments',
    'merchants',
    'market_products',
    'market_orders',
    'content_reports',
    'wallets',
    'credit_transactions',
    'app_config',
    'pages',
    'page_follows',
    'groups',
    'group_members',
    'showcase_accounts'
  ];
BEGIN
  FOREACH tbl IN ARRAY momi_tables LOOP
    IF EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'public' AND table_name = tbl
    ) THEN
      EXECUTE format('ALTER TABLE public.%I REPLICA IDENTITY FULL', tbl);
      IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = tbl
      ) THEN
        EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', tbl);
      END IF;
    END IF;
  END LOOP;
END $$;
