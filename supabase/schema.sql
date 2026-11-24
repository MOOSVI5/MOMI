-- ============================================================================
-- Momi (Android + Web) -> Supabase PostgreSQL + RLS + Storage + RPC Migration
-- ============================================================================
-- HOW TO APPLY IN SUPABASE DASHBOARD:
--   1. Create a Supabase project (https://supabase.com/dashboard).
--   2. Open SQL Editor -> New Query, paste this entire file, and click Run.
--   3. Authentication -> Providers -> Google:
--        * Enable Google provider.
--        * Add your Android Google OAuth Client ID and Web OAuth Client ID.
--        * Under Authentication -> URL Configuration:
--            Site URL: https://moosvi5.github.io/MOMI/
--            Redirect URLs: https://moosvi5.github.io/MOMI/
--   4. Project Settings -> API:
--        * Copy Project URL   -> set SUPABASE_URL in AI Studio Secrets.
--        * Copy anon public   -> set SUPABASE_ANON_KEY in AI Studio Secrets.
--        * NEVER put service_role or sb_secret_* keys in Android or Web!
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- 1. Helper Functions (Zero-Trust Admin, Staff & Updated-At Triggers)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_momi_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT auth.uid() IS NOT NULL
    AND lower(coalesce(auth.jwt() ->> 'email', '')) = 'syali6511@gmail.com'
    AND coalesce((auth.jwt() ->> 'email_verified')::boolean, false) = true;
$$;

CREATE OR REPLACE FUNCTION public.touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

-- ----------------------------------------------------------------------------
-- 2. Tables (1:1 Parity with Momi Firestore Collections)
-- ----------------------------------------------------------------------------

-- 2.1 Public Creator Profiles (no PII)
CREATE TABLE IF NOT EXISTS public.profiles (
  user_id text PRIMARY KEY CHECK (length(user_id) BETWEEN 1 AND 128),
  username text NOT NULL UNIQUE CHECK (length(username) BETWEEN 1 AND 50),
  display_name text NOT NULL CHECK (length(display_name) BETWEEN 1 AND 80),
  category text NOT NULL DEFAULT '' CHECK (length(category) <= 80),
  bio text NOT NULL DEFAULT '' CHECK (length(bio) <= 300),
  website text NOT NULL DEFAULT '' CHECK (length(website) <= 200),
  avatar_key text NOT NULL DEFAULT 'avatar_aria' CHECK (length(avatar_key) BETWEEN 1 AND 64),
  avatar_url text,
  avatar_data text,
  followers_count bigint NOT NULL DEFAULT 0 CHECK (followers_count >= 0),
  following_count bigint NOT NULL DEFAULT 0 CHECK (following_count >= 0),
  is_verified boolean NOT NULL DEFAULT false,
  role text NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'MODERATOR', 'EDITOR')),
  moderation_scopes text[] NOT NULL DEFAULT ARRAY[]::text[],
  assigned_by_email text,
  partner_consent boolean NOT NULL DEFAULT false,
  consent_updated_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION public.is_staff_with_scope(scope_name text)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT auth.uid() IS NOT NULL AND EXISTS (
    SELECT 1
    FROM public.profiles p
    WHERE p.user_id = auth.uid()::text
      AND p.role IN ('MODERATOR', 'EDITOR')
      AND scope_name = ANY(p.moderation_scopes)
  );
$$;

-- 2.2 Global Feed Posts & Clips
CREATE TABLE IF NOT EXISTS public.posts (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id text NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  author_username text NOT NULL CHECK (length(author_username) BETWEEN 1 AND 50),
  author_display_name text NOT NULL CHECK (length(author_display_name) BETWEEN 1 AND 80),
  author_avatar_key text NOT NULL DEFAULT 'avatar_aria' CHECK (length(author_avatar_key) BETWEEN 1 AND 64),
  author_verified boolean NOT NULL DEFAULT false,
  location text NOT NULL DEFAULT '' CHECK (length(location) <= 120),
  image_key text NOT NULL DEFAULT 'img_post_coastal' CHECK (length(image_key) BETWEEN 1 AND 64),
  image_url text,
  image_data text,
  extra_image_urls text[] NOT NULL DEFAULT ARRAY[]::text[],
  extra_image_datas text[] NOT NULL DEFAULT ARRAY[]::text[],
  media_type text NOT NULL DEFAULT 'IMAGE' CHECK (media_type IN ('IMAGE', 'VIDEO')),
  video_url text,
  video_data text,
  video_duration_ms bigint NOT NULL DEFAULT 0 CHECK (video_duration_ms BETWEEN 0 AND 3600000),
  filter_tag text NOT NULL DEFAULT 'NONE' CHECK (
    filter_tag IN ('NONE', 'BLUE', 'MAROON', 'MONO', 'WARM', 'VINTAGE', 'VIVID', 'NOIR', 'CINEMA', 'PASTEL')
  ),
  aspect_ratio text NOT NULL DEFAULT '1:1' CHECK (aspect_ratio IN ('1:1', '4:5', '16:9')),
  tagged_usernames text[] NOT NULL DEFAULT ARRAY[]::text[],
  hashtags text[] NOT NULL DEFAULT ARRAY[]::text[],
  caption text NOT NULL DEFAULT '' CHECK (length(caption) <= 1000),
  likes_count bigint NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
  views_count bigint NOT NULL DEFAULT 0 CHECK (views_count >= 0),
  comments_count bigint NOT NULL DEFAULT 0 CHECK (comments_count >= 0),
  category text NOT NULL DEFAULT 'For You' CHECK (length(category) BETWEEN 1 AND 40),
  is_reel boolean NOT NULL DEFAULT false,
  audio_title text NOT NULL DEFAULT '' CHECK (length(audio_title) <= 120),
  audio_url text,
  audio_data text,
  is_boosted boolean NOT NULL DEFAULT false,
  boost_tier text NOT NULL DEFAULT '' CHECK (boost_tier IN ('', 'SPOTLIGHT', 'SUPER_BOOST', 'OWNER_RECOMMENDED')),
  recommended_label text NOT NULL DEFAULT '' CHECK (length(recommended_label) <= 40),
  boosted_by_uid text,
  boosted_at timestamptz,
  page_id text,
  group_id text,
  community_name text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON public.posts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_user_id ON public.posts(user_id);
CREATE INDEX IF NOT EXISTS idx_posts_boost ON public.posts(is_boosted, boost_tier, created_at DESC);

-- 2.3 Post Comments
CREATE TABLE IF NOT EXISTS public.comments (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  post_id text NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
  user_id text NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  author_username text NOT NULL CHECK (length(author_username) BETWEEN 1 AND 50),
  author_avatar_key text NOT NULL DEFAULT 'avatar_aria' CHECK (length(author_avatar_key) BETWEEN 1 AND 64),
  text text NOT NULL CHECK (length(text) BETWEEN 1 AND 500),
  likes_count bigint NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_comments_post_id ON public.comments(post_id, created_at ASC);

-- 2.4 Stories
CREATE TABLE IF NOT EXISTS public.stories (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id text NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  username text NOT NULL CHECK (length(username) BETWEEN 1 AND 50),
  avatar_key text NOT NULL DEFAULT 'avatar_aria' CHECK (length(avatar_key) BETWEEN 1 AND 64),
  image_key text NOT NULL DEFAULT 'img_post_coastal' CHECK (length(image_key) BETWEEN 1 AND 64),
  image_url text,
  image_data text,
  media_type text NOT NULL DEFAULT 'IMAGE' CHECK (media_type IN ('IMAGE', 'VIDEO')),
  video_url text,
  video_data text,
  video_duration_ms bigint NOT NULL DEFAULT 0 CHECK (video_duration_ms BETWEEN 0 AND 3600000),
  audio_title text NOT NULL DEFAULT '' CHECK (length(audio_title) <= 120),
  audio_data text,
  caption text NOT NULL DEFAULT '' CHECK (length(caption) <= 300),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_stories_created_at ON public.stories(created_at DESC);

-- 2.5 Follow Edges
CREATE TABLE IF NOT EXISTS public.follows (
  id text PRIMARY KEY,
  follower_uid text NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  target_uid text NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  follower_username text NOT NULL CHECK (length(follower_username) BETWEEN 1 AND 50),
  target_username text NOT NULL CHECK (length(target_username) BETWEEN 1 AND 50),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_no_self_follow CHECK (follower_uid <> target_uid),
  CONSTRAINT chk_follow_id_match CHECK (id = follower_uid || '_' || target_uid)
);
CREATE INDEX IF NOT EXISTS idx_follows_follower ON public.follows(follower_uid);
CREATE INDEX IF NOT EXISTS idx_follows_target ON public.follows(target_uid);

-- 2.6 Per-User Interaction Markers (Liked Posts, Bookmarks, Seen Stories, Liked Comments)
CREATE TABLE IF NOT EXISTS public.user_interactions (
  user_id text NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  target_type text NOT NULL CHECK (target_type IN ('LIKED_POST', 'BOOKMARK', 'SEEN_STORY', 'LIKED_COMMENT')),
  target_id text NOT NULL CHECK (length(target_id) BETWEEN 1 AND 128),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, target_type, target_id)
);

-- 2.7 User Notifications Inbox
CREATE TABLE IF NOT EXISTS public.notifications (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id text NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  actor_uid text NOT NULL CHECK (length(actor_uid) BETWEEN 1 AND 128),
  actor_username text NOT NULL CHECK (length(actor_username) BETWEEN 1 AND 50),
  actor_avatar_key text NOT NULL DEFAULT 'avatar_aria' CHECK (length(actor_avatar_key) BETWEEN 1 AND 64),
  message text NOT NULL CHECK (length(message) BETWEEN 1 AND 300),
  time_label text NOT NULL DEFAULT 'Just now' CHECK (length(time_label) BETWEEN 1 AND 30),
  section text NOT NULL DEFAULT 'Today' CHECK (length(section) BETWEEN 1 AND 30),
  preview_image_key text,
  is_follow_action boolean NOT NULL DEFAULT false,
  is_following_back boolean NOT NULL DEFAULT false,
  is_unread boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON public.notifications(user_id, created_at DESC);

-- 2.8 Direct Message Threads (1:1 Participant Isolated)
CREATE TABLE IF NOT EXISTS public.threads (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  participant_uids text[] NOT NULL CHECK (array_length(participant_uids, 1) = 2),
  user_a_id text NOT NULL,
  user_a_username text NOT NULL,
  user_a_display_name text NOT NULL,
  user_a_avatar_key text NOT NULL DEFAULT 'avatar_aria',
  user_b_id text NOT NULL,
  user_b_username text NOT NULL,
  user_b_display_name text NOT NULL,
  user_b_avatar_key text NOT NULL DEFAULT 'avatar_aria',
  last_message text NOT NULL DEFAULT '' CHECK (length(last_message) <= 500),
  last_sender_uid text NOT NULL DEFAULT '',
  unread_uid text NOT NULL DEFAULT '',
  time_label text NOT NULL DEFAULT 'Now',
  unread_count bigint NOT NULL DEFAULT 0 CHECK (unread_count >= 0),
  is_online boolean NOT NULL DEFAULT false,
  note_text text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_distinct_participants CHECK (user_a_id <> user_b_id)
);
CREATE INDEX IF NOT EXISTS idx_threads_participants ON public.threads USING GIN (participant_uids);

-- 2.9 Direct Chat Messages
CREATE TABLE IF NOT EXISTS public.chat_messages (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  thread_id text NOT NULL REFERENCES public.threads(id) ON DELETE CASCADE,
  participant_uids text[] NOT NULL CHECK (array_length(participant_uids, 1) = 2),
  sender_uid text NOT NULL,
  sender_username text NOT NULL CHECK (length(sender_username) BETWEEN 1 AND 50),
  text text NOT NULL CHECK (length(text) BETWEEN 1 AND 1000),
  message_type text NOT NULL DEFAULT 'TEXT' CHECK (message_type IN ('TEXT', 'IMAGE', 'VIDEO', 'AUDIO')),
  media_url text,
  image_data text,
  video_data text,
  video_duration_ms bigint DEFAULT 0,
  audio_data text,
  audio_duration_ms bigint DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_sender_in_thread CHECK (sender_uid = ANY(participant_uids))
);
CREATE INDEX IF NOT EXISTS idx_chat_messages_thread ON public.chat_messages(thread_id, created_at ASC);

-- 2.10 Live Broadcast Rooms, Rolling Frames & Chat
CREATE TABLE IF NOT EXISTS public.live_streams (
  id text PRIMARY KEY,
  user_id text NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  username text NOT NULL,
  display_name text NOT NULL,
  avatar_key text NOT NULL DEFAULT 'avatar_aria',
  author_verified boolean NOT NULL DEFAULT false,
  title text NOT NULL DEFAULT '' CHECK (length(title) <= 140),
  category text NOT NULL DEFAULT 'Live' CHECK (length(category) BETWEEN 1 AND 40),
  is_live boolean NOT NULL DEFAULT true,
  viewer_count bigint NOT NULL DEFAULT 0 CHECK (viewer_count >= 0),
  likes_count bigint NOT NULL DEFAULT 0 CHECK (likes_count >= 0),
  gifts_credits bigint NOT NULL DEFAULT 0 CHECK (gifts_credits >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_live_stream_owner_id CHECK (id = user_id)
);

CREATE TABLE IF NOT EXISTS public.live_frames (
  stream_id text PRIMARY KEY REFERENCES public.live_streams(id) ON DELETE CASCADE,
  user_id text NOT NULL,
  seq bigint NOT NULL DEFAULT 0 CHECK (seq >= 0),
  frame_data text NOT NULL DEFAULT '',
  camera_enabled boolean NOT NULL DEFAULT true,
  mic_muted boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_live_frame_owner CHECK (stream_id = user_id)
);

CREATE TABLE IF NOT EXISTS public.live_comments (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  stream_id text NOT NULL REFERENCES public.live_streams(id) ON DELETE CASCADE,
  user_id text NOT NULL,
  username text NOT NULL CHECK (length(username) BETWEEN 1 AND 50),
  avatar_key text NOT NULL DEFAULT 'avatar_aria',
  text text NOT NULL CHECK (length(text) BETWEEN 1 AND 300),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_live_comments_stream ON public.live_comments(stream_id, created_at ASC);

-- 2.11 Marketplace Merchants, Products & Orders
CREATE TABLE IF NOT EXISTS public.merchants (
  user_id text PRIMARY KEY REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  owner_username text NOT NULL CHECK (length(owner_username) BETWEEN 1 AND 50),
  store_name text NOT NULL CHECK (length(store_name) BETWEEN 1 AND 80),
  tagline text NOT NULL DEFAULT '' CHECK (length(tagline) <= 160),
  category text NOT NULL DEFAULT 'Collectibles' CHECK (length(category) BETWEEN 1 AND 40),
  location text NOT NULL DEFAULT '' CHECK (length(location) <= 120),
  avatar_key text NOT NULL DEFAULT 'avatar_aria',
  avatar_url text,
  avatar_data text,
  is_verified boolean NOT NULL DEFAULT false,
  verified_by_email text,
  total_sales bigint NOT NULL DEFAULT 0 CHECK (total_sales >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.market_products (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  seller_uid text NOT NULL REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  seller_username text NOT NULL CHECK (length(seller_username) BETWEEN 1 AND 50),
  store_name text NOT NULL CHECK (length(store_name) BETWEEN 1 AND 80),
  title text NOT NULL CHECK (length(title) BETWEEN 1 AND 120),
  description text NOT NULL DEFAULT '' CHECK (length(description) <= 1000),
  category text NOT NULL DEFAULT 'Collectibles' CHECK (length(category) BETWEEN 1 AND 40),
  price_usd numeric(12,2) NOT NULL CHECK (price_usd > 0),
  original_price_usd numeric(12,2) NOT NULL DEFAULT 0 CHECK (original_price_usd >= 0),
  discount_percent integer NOT NULL DEFAULT 0 CHECK (discount_percent BETWEEN 0 AND 95),
  free_shipping boolean NOT NULL DEFAULT true,
  image_key text NOT NULL DEFAULT 'img_post_coastal',
  image_url text,
  image_data text,
  extra_image_datas text[] NOT NULL DEFAULT ARRAY[]::text[],
  video_url text,
  video_data text,
  video_duration_ms bigint NOT NULL DEFAULT 0 CHECK (video_duration_ms BETWEEN 0 AND 3600000),
  stock_count integer NOT NULL DEFAULT 1 CHECK (stock_count BETWEEN 0 AND 99999),
  is_featured boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_market_products_seller ON public.market_products(seller_uid);

CREATE TABLE IF NOT EXISTS public.market_orders (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  buyer_uid text NOT NULL,
  buyer_username text NOT NULL,
  seller_uid text NOT NULL,
  seller_store_name text NOT NULL,
  product_id text NOT NULL,
  product_title text NOT NULL,
  product_image_key text NOT NULL DEFAULT 'img_post_coastal',
  product_image_data text,
  price_usd numeric(12,2) NOT NULL CHECK (price_usd > 0),
  shipping_address text NOT NULL CHECK (length(shipping_address) BETWEEN 1 AND 300),
  status text NOT NULL DEFAULT 'PLACED' CHECK (status IN ('PLACED', 'SHIPPED', 'DELIVERED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_distinct_order_parties CHECK (buyer_uid <> seller_uid)
);
CREATE INDEX IF NOT EXISTS idx_market_orders_buyer ON public.market_orders(buyer_uid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_market_orders_seller ON public.market_orders(seller_uid, created_at DESC);

-- 2.12 Safety Moderation Surveillance Queue
CREATE TABLE IF NOT EXISTS public.content_reports (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  reporter_uid text NOT NULL,
  reporter_username text NOT NULL,
  target_type text NOT NULL CHECK (target_type IN ('POST', 'PRODUCT', 'COMMENT', 'USER')),
  target_id text NOT NULL,
  target_author_uid text NOT NULL DEFAULT '',
  target_author_username text NOT NULL DEFAULT '',
  target_preview_text text NOT NULL DEFAULT '' CHECK (length(target_preview_text) <= 240),
  category text NOT NULL CHECK (category IN ('VIOLENCE_GRAPHIC', 'SEXUAL_NUDITY', 'HARASSMENT_SPAM', 'MARKETPLACE_SAFETY')),
  reason_detail text NOT NULL DEFAULT '' CHECK (length(reason_detail) <= 400),
  status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'REMOVED', 'DISMISSED')),
  reviewed_by_uid text,
  reviewed_by_username text,
  reviewed_by_role text CHECK (reviewed_by_role IS NULL OR reviewed_by_role IN ('ADMIN', 'MODERATOR', 'EDITOR')),
  resolution_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- 2.13 Owner-Private Wallets & Immutable Credit Transactions
CREATE TABLE IF NOT EXISTS public.wallets (
  user_id text PRIMARY KEY REFERENCES public.profiles(user_id) ON DELETE CASCADE,
  balance bigint NOT NULL DEFAULT 0 CHECK (balance >= 0),
  total_earned bigint NOT NULL DEFAULT 0 CHECK (total_earned >= 0),
  total_spent bigint NOT NULL DEFAULT 0 CHECK (total_spent >= 0),
  total_purchased bigint NOT NULL DEFAULT 0 CHECK (total_purchased >= 0),
  account_tier text NOT NULL DEFAULT 'STANDARD' CHECK (account_tier IN ('STANDARD', 'SUPPORTER', 'CREATOR_PRO', 'VIP')),
  verification_tier text NOT NULL DEFAULT 'NONE' CHECK (verification_tier IN ('NONE', 'PENDING_PERK', 'CREATOR_PRO', 'VERIFIED')),
  unlocked_perks text[] NOT NULL DEFAULT ARRAY[]::text[],
  last_tx_id text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.credit_transactions (
  tx_id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  type text NOT NULL CHECK (type IN ('TOP_UP', 'GIFT', 'PERK_UNLOCK', 'POST_BOOST', 'OWNER_GRANT')),
  sender_uid text NOT NULL,
  sender_username text NOT NULL,
  recipient_uid text NOT NULL,
  recipient_username text NOT NULL,
  credits_amount bigint NOT NULL CHECK (credits_amount BETWEEN 0 AND 5000),
  price_usd numeric(12,2) NOT NULL DEFAULT 0 CHECK (price_usd >= 0),
  payment_method text NOT NULL CHECK (payment_method IN ('GOOGLE_PLAY', 'DEBUG_SANDBOX', 'WALLET_CREDITS', 'OWNER_ADMIN')),
  payment_ref text,
  gift_key text,
  gift_name text,
  stream_id text,
  perk_key text,
  post_id text,
  recipient_claimed boolean NOT NULL DEFAULT true,
  status text NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'PENDING')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_credit_tx_sender ON public.credit_transactions(sender_uid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_credit_tx_recipient ON public.credit_transactions(recipient_uid, created_at DESC);

-- 2.14 Remote Monetization Configuration (AdMob, Start.io, Monetag & Gamezop)
CREATE TABLE IF NOT EXISTS public.app_config (
  id text PRIMARY KEY DEFAULT 'monetization',
  admob_enabled boolean NOT NULL DEFAULT true,
  admob_banner_unit_id text NOT NULL DEFAULT '' CHECK (length(admob_banner_unit_id) <= 120),
  admob_mrec_unit_id text NOT NULL DEFAULT '' CHECK (length(admob_mrec_unit_id) <= 120),
  admob_interstitial_unit_id text NOT NULL DEFAULT '' CHECK (length(admob_interstitial_unit_id) <= 120),
  start_io_enabled boolean NOT NULL DEFAULT true,
  monetag_enabled boolean NOT NULL DEFAULT true,
  monetag_direct_link_url text NOT NULL DEFAULT '' CHECK (
    monetag_direct_link_url = '' OR monetag_direct_link_url ~ '^https://'
  ),
  monetag_web_zone_id text NOT NULL DEFAULT '' CHECK (length(monetag_web_zone_id) <= 40),
  monetag_web_meta_tag text NOT NULL DEFAULT '' CHECK (length(monetag_web_meta_tag) <= 120),
  partner_promo_title text NOT NULL DEFAULT 'Featured Creator Deals & Partner Showcase',
  partner_promo_subtitle text NOT NULL DEFAULT 'Explore verified creator collections, partner offers & exclusive community perks.',
  partner_promo_cta text NOT NULL DEFAULT 'Explore Partner',
  gamezop_enabled boolean NOT NULL DEFAULT false,
  gamezop_portal_url text NOT NULL DEFAULT '' CHECK (
    gamezop_portal_url = '' OR gamezop_portal_url ~ '^https://'
  ),
  gamezop_publisher_id text NOT NULL DEFAULT '' CHECK (length(gamezop_publisher_id) <= 40),
  gamezop_banner_title text NOT NULL DEFAULT 'Momi Arcade · Play Instant Games',
  announcement_id text NOT NULL DEFAULT '',
  announcement_title text NOT NULL DEFAULT '',
  announcement_body text NOT NULL DEFAULT '',
  latest_version_name text NOT NULL DEFAULT '2.1',
  release_notes text NOT NULL DEFAULT '',
  updated_by_email text NOT NULL DEFAULT '',
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO public.app_config (id) VALUES ('monetization') ON CONFLICT (id) DO NOTHING;

-- 2.15 Creator Pages, Community Groups & Authorized Showcase Accounts
CREATE TABLE IF NOT EXISTS public.pages (
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  owner_uid text NOT NULL,
  owner_username text NOT NULL DEFAULT 'momi.creator',
  name text NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
  handle text NOT NULL DEFAULT '' CHECK (length(handle) <= 50),
  category text NOT NULL DEFAULT 'Creator' CHECK (length(category) <= 40),
  bio text NOT NULL DEFAULT '' CHECK (length(bio) <= 300),
  cover_key text NOT NULL DEFAULT 'img_post_coastal',
  avatar_url text,
  is_verified boolean NOT NULL DEFAULT false,
  followers_count bigint NOT NULL DEFAULT 0 CHECK (followers_count >= 0),
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
  id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
  owner_uid text NOT NULL,
  owner_username text NOT NULL DEFAULT 'momi.creator',
  name text NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
  category text NOT NULL DEFAULT 'Community' CHECK (length(category) <= 40),
  description text NOT NULL DEFAULT '' CHECK (length(description) <= 300),
  privacy text NOT NULL DEFAULT 'PUBLIC' CHECK (privacy IN ('PUBLIC', 'PRIVATE')),
  cover_key text NOT NULL DEFAULT 'img_post_architecture',
  members_count bigint NOT NULL DEFAULT 1 CHECK (members_count >= 0),
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
  bio text NOT NULL DEFAULT '',
  category text NOT NULL DEFAULT 'Creator',
  location text NOT NULL DEFAULT '',
  is_authorized boolean NOT NULL DEFAULT false,
  updated_by_email text NOT NULL DEFAULT '',
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Attach updated_at triggers
DO $$
DECLARE
  tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY[
    'profiles', 'posts', 'comments', 'stories', 'notifications',
    'threads', 'live_streams', 'merchants', 'market_products',
    'market_orders', 'content_reports', 'wallets', 'credit_transactions',
    'app_config', 'pages', 'groups', 'showcase_accounts'
  ] LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS trg_touch_%I ON public.%I;', tbl, tbl);
    EXECUTE format(
      'CREATE TRIGGER trg_touch_%I BEFORE UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.touch_updated_at();',
      tbl, tbl
    );
  END LOOP;
END $$;

-- ----------------------------------------------------------------------------
-- 3. Zero-Trust Row Level Security (RLS)
-- ----------------------------------------------------------------------------
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_interactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.live_streams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.live_frames ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.live_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.merchants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.market_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.market_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wallets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.credit_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.page_follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.showcase_accounts ENABLE ROW LEVEL SECURITY;

-- Profiles
DROP POLICY IF EXISTS profiles_select_auth ON public.profiles;
CREATE POLICY profiles_select_auth ON public.profiles
  FOR SELECT TO anon, authenticated USING (true);

DROP POLICY IF EXISTS profiles_insert_owner ON public.profiles;
CREATE POLICY profiles_insert_owner ON public.profiles
  FOR INSERT TO authenticated
  WITH CHECK (
    public.is_momi_admin()
    OR (
      user_id = auth.uid()::text
      AND (is_verified = false)
      AND (role = 'USER')
    )
  );

DROP POLICY IF EXISTS profiles_update_owner_or_admin ON public.profiles;
CREATE POLICY profiles_update_owner_or_admin ON public.profiles
  FOR UPDATE TO authenticated
  USING (user_id = auth.uid()::text OR public.is_momi_admin())
  WITH CHECK (
    public.is_momi_admin()
    OR (
      user_id = auth.uid()::text
      AND is_verified = (SELECT p.is_verified FROM public.profiles p WHERE p.user_id = auth.uid()::text)
      AND role = (SELECT p.role FROM public.profiles p WHERE p.user_id = auth.uid()::text)
      AND moderation_scopes = (SELECT p.moderation_scopes FROM public.profiles p WHERE p.user_id = auth.uid()::text)
    )
  );

-- Posts
DROP POLICY IF EXISTS posts_select_auth ON public.posts;
CREATE POLICY posts_select_auth ON public.posts
  FOR SELECT TO anon, authenticated USING (true);

DROP POLICY IF EXISTS posts_insert_owner ON public.posts;
CREATE POLICY posts_insert_owner ON public.posts
  FOR INSERT TO authenticated
  WITH CHECK (
    public.is_momi_admin()
    OR (
      user_id = auth.uid()::text
      AND is_boosted = false
    )
  );

DROP POLICY IF EXISTS posts_update_owner_or_admin ON public.posts;
CREATE POLICY posts_update_owner_or_admin ON public.posts
  FOR UPDATE TO authenticated
  USING (user_id = auth.uid()::text OR public.is_momi_admin())
  WITH CHECK (
    public.is_momi_admin()
    OR (
      user_id = auth.uid()::text
      AND boost_tier IN ('', 'SPOTLIGHT', 'SUPER_BOOST')
    )
  );

DROP POLICY IF EXISTS posts_delete_owner_or_staff ON public.posts;
CREATE POLICY posts_delete_owner_or_staff ON public.posts
  FOR DELETE TO authenticated
  USING (
    user_id = auth.uid()::text
    OR public.is_momi_admin()
    OR public.is_staff_with_scope('VIOLENCE_GRAPHIC')
    OR public.is_staff_with_scope('SEXUAL_NUDITY')
    OR public.is_staff_with_scope('HARASSMENT_SPAM')
  );

-- Comments
DROP POLICY IF EXISTS comments_select_auth ON public.comments;
CREATE POLICY comments_select_auth ON public.comments FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS comments_insert_owner ON public.comments;
CREATE POLICY comments_insert_owner ON public.comments FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid()::text);
DROP POLICY IF EXISTS comments_delete_owner_or_staff ON public.comments;
CREATE POLICY comments_delete_owner_or_staff ON public.comments
  FOR DELETE TO authenticated
  USING (user_id = auth.uid()::text OR public.is_momi_admin() OR public.is_staff_with_scope('HARASSMENT_SPAM'));

-- Stories
DROP POLICY IF EXISTS stories_select_auth ON public.stories;
CREATE POLICY stories_select_auth ON public.stories FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS stories_insert_owner ON public.stories;
CREATE POLICY stories_insert_owner ON public.stories FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid()::text OR public.is_momi_admin());
DROP POLICY IF EXISTS stories_delete_owner_or_admin ON public.stories;
CREATE POLICY stories_delete_owner_or_admin ON public.stories FOR DELETE TO authenticated USING (user_id = auth.uid()::text OR public.is_momi_admin());

-- Follows
DROP POLICY IF EXISTS follows_select_auth ON public.follows;
CREATE POLICY follows_select_auth ON public.follows FOR SELECT TO authenticated USING (true);
DROP POLICY IF EXISTS follows_insert_owner ON public.follows;
CREATE POLICY follows_insert_owner ON public.follows FOR INSERT TO authenticated WITH CHECK (follower_uid = auth.uid()::text);
DROP POLICY IF EXISTS follows_delete_owner ON public.follows;
CREATE POLICY follows_delete_owner ON public.follows FOR DELETE TO authenticated USING (follower_uid = auth.uid()::text);

-- User Interactions (owner-only)
DROP POLICY IF EXISTS interactions_owner_all ON public.user_interactions;
CREATE POLICY interactions_owner_all ON public.user_interactions
  FOR ALL TO authenticated
  USING (user_id = auth.uid()::text)
  WITH CHECK (user_id = auth.uid()::text);

-- Notifications (owner-read/update/delete, actor-insert)
DROP POLICY IF EXISTS notifications_select_owner ON public.notifications;
CREATE POLICY notifications_select_owner ON public.notifications FOR SELECT TO authenticated USING (user_id = auth.uid()::text);
DROP POLICY IF EXISTS notifications_insert_actor ON public.notifications;
CREATE POLICY notifications_insert_actor ON public.notifications FOR INSERT TO authenticated WITH CHECK (actor_uid = auth.uid()::text);
DROP POLICY IF EXISTS notifications_update_owner ON public.notifications;
CREATE POLICY notifications_update_owner ON public.notifications FOR UPDATE TO authenticated USING (user_id = auth.uid()::text) WITH CHECK (user_id = auth.uid()::text);
DROP POLICY IF EXISTS notifications_delete_owner ON public.notifications;
CREATE POLICY notifications_delete_owner ON public.notifications FOR DELETE TO authenticated USING (user_id = auth.uid()::text);

-- 1:1 Direct Message Threads & Messages
DROP POLICY IF EXISTS threads_participant_select ON public.threads;
CREATE POLICY threads_participant_select ON public.threads FOR SELECT TO authenticated USING (auth.uid()::text = ANY(participant_uids));
DROP POLICY IF EXISTS threads_participant_insert ON public.threads;
CREATE POLICY threads_participant_insert ON public.threads FOR INSERT TO authenticated WITH CHECK (auth.uid()::text = ANY(participant_uids));
DROP POLICY IF EXISTS threads_participant_update ON public.threads;
CREATE POLICY threads_participant_update ON public.threads FOR UPDATE TO authenticated USING (auth.uid()::text = ANY(participant_uids)) WITH CHECK (auth.uid()::text = ANY(participant_uids));

DROP POLICY IF EXISTS messages_participant_select ON public.chat_messages;
CREATE POLICY messages_participant_select ON public.chat_messages FOR SELECT TO authenticated USING (auth.uid()::text = ANY(participant_uids));
DROP POLICY IF EXISTS messages_sender_insert ON public.chat_messages;
CREATE POLICY messages_sender_insert ON public.chat_messages FOR INSERT TO authenticated WITH CHECK (sender_uid = auth.uid()::text AND auth.uid()::text = ANY(participant_uids));

-- Live Streams, Frames, Comments
DROP POLICY IF EXISTS live_streams_select_auth ON public.live_streams;
CREATE POLICY live_streams_select_auth ON public.live_streams FOR SELECT TO authenticated USING (true);
DROP POLICY IF EXISTS live_streams_host_write ON public.live_streams;
CREATE POLICY live_streams_host_write ON public.live_streams FOR ALL TO authenticated USING (user_id = auth.uid()::text OR public.is_momi_admin()) WITH CHECK (user_id = auth.uid()::text OR public.is_momi_admin());

DROP POLICY IF EXISTS live_frames_select_auth ON public.live_frames;
CREATE POLICY live_frames_select_auth ON public.live_frames FOR SELECT TO authenticated USING (true);
DROP POLICY IF EXISTS live_frames_host_write ON public.live_frames;
CREATE POLICY live_frames_host_write ON public.live_frames FOR ALL TO authenticated USING (user_id = auth.uid()::text) WITH CHECK (user_id = auth.uid()::text);

DROP POLICY IF EXISTS live_comments_select_auth ON public.live_comments;
CREATE POLICY live_comments_select_auth ON public.live_comments FOR SELECT TO authenticated USING (true);
DROP POLICY IF EXISTS live_comments_insert_auth ON public.live_comments;
CREATE POLICY live_comments_insert_auth ON public.live_comments FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid()::text);

-- Marketplace Merchants, Products & Private Orders
DROP POLICY IF EXISTS merchants_select_auth ON public.merchants;
CREATE POLICY merchants_select_auth ON public.merchants FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS merchants_insert_owner ON public.merchants;
CREATE POLICY merchants_insert_owner ON public.merchants FOR INSERT TO authenticated WITH CHECK (public.is_momi_admin() OR (user_id = auth.uid()::text AND is_verified = false));
DROP POLICY IF EXISTS merchants_update_owner_or_admin ON public.merchants;
CREATE POLICY merchants_update_owner_or_admin ON public.merchants
  FOR UPDATE TO authenticated
  USING (user_id = auth.uid()::text OR public.is_momi_admin())
  WITH CHECK (public.is_momi_admin() OR (user_id = auth.uid()::text AND is_verified = (SELECT m.is_verified FROM public.merchants m WHERE m.user_id = auth.uid()::text)));

DROP POLICY IF EXISTS products_select_auth ON public.market_products;
CREATE POLICY products_select_auth ON public.market_products FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS products_insert_seller ON public.market_products;
CREATE POLICY products_insert_seller ON public.market_products FOR INSERT TO authenticated WITH CHECK (seller_uid = auth.uid()::text OR public.is_momi_admin());
DROP POLICY IF EXISTS products_update_seller_or_editor ON public.market_products;
CREATE POLICY products_update_seller_or_editor ON public.market_products
  FOR UPDATE TO authenticated
  USING (seller_uid = auth.uid()::text OR public.is_momi_admin() OR public.is_staff_with_scope('EDITORIAL_CURATION'));
DROP POLICY IF EXISTS products_delete_seller_or_safety ON public.market_products;
CREATE POLICY products_delete_seller_or_safety ON public.market_products
  FOR DELETE TO authenticated
  USING (seller_uid = auth.uid()::text OR public.is_momi_admin() OR public.is_staff_with_scope('MARKETPLACE_SAFETY'));

DROP POLICY IF EXISTS orders_select_parties ON public.market_orders;
CREATE POLICY orders_select_parties ON public.market_orders FOR SELECT TO authenticated USING (buyer_uid = auth.uid()::text OR seller_uid = auth.uid()::text);
DROP POLICY IF EXISTS orders_insert_buyer ON public.market_orders;
CREATE POLICY orders_insert_buyer ON public.market_orders FOR INSERT TO authenticated WITH CHECK (buyer_uid = auth.uid()::text);
DROP POLICY IF EXISTS orders_update_seller ON public.market_orders;
CREATE POLICY orders_update_seller ON public.market_orders FOR UPDATE TO authenticated USING (seller_uid = auth.uid()::text) WITH CHECK (seller_uid = auth.uid()::text);

-- Safety Surveillance Reports
DROP POLICY IF EXISTS reports_insert_reporter ON public.content_reports;
CREATE POLICY reports_insert_reporter ON public.content_reports FOR INSERT TO authenticated WITH CHECK (reporter_uid = auth.uid()::text);
DROP POLICY IF EXISTS reports_select_reporter_or_staff ON public.content_reports;
CREATE POLICY reports_select_reporter_or_staff ON public.content_reports
  FOR SELECT TO authenticated
  USING (reporter_uid = auth.uid()::text OR public.is_momi_admin() OR public.is_staff_with_scope(category));
DROP POLICY IF EXISTS reports_update_staff ON public.content_reports;
CREATE POLICY reports_update_staff ON public.content_reports
  FOR UPDATE TO authenticated
  USING (public.is_momi_admin() OR public.is_staff_with_scope(category));

-- Wallets & Credit Transactions (Mutations happen via SECURITY DEFINER RPCs)
DROP POLICY IF EXISTS wallets_select_owner_or_admin ON public.wallets;
CREATE POLICY wallets_select_owner_or_admin ON public.wallets
  FOR SELECT TO authenticated
  USING (user_id = auth.uid()::text OR public.is_momi_admin());

DROP POLICY IF EXISTS wallets_insert_owner ON public.wallets;
CREATE POLICY wallets_insert_owner ON public.wallets
  FOR INSERT TO authenticated
  WITH CHECK (user_id = auth.uid()::text AND balance = 0 AND total_earned = 0 AND total_spent = 0 AND total_purchased = 0);

DROP POLICY IF EXISTS credit_tx_select_parties ON public.credit_transactions;
CREATE POLICY credit_tx_select_parties ON public.credit_transactions
  FOR SELECT TO authenticated
  USING (sender_uid = auth.uid()::text OR recipient_uid = auth.uid()::text OR public.is_momi_admin());

-- App Config (Readable by authenticated + anon for web shell preload; writable ONLY by Momi Admin)
DROP POLICY IF EXISTS app_config_select_public ON public.app_config;
CREATE POLICY app_config_select_public ON public.app_config FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS app_config_admin_write ON public.app_config;
CREATE POLICY app_config_admin_write ON public.app_config FOR ALL TO authenticated USING (public.is_momi_admin()) WITH CHECK (public.is_momi_admin());

-- ----------------------------------------------------------------------------
-- 4. Atomic Server-Side RPCs (Replace Client-Side Firestore Multi-Doc Batches)
-- ----------------------------------------------------------------------------

-- 4.1 Boost Post with Wallet Credits
CREATE OR REPLACE FUNCTION public.boost_momi_post(p_post_id text, p_tier text, p_credits bigint)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid text := auth.uid()::text;
  v_balance bigint;
  v_username text;
  v_label text;
  v_tx_id text := 'tx_boost_' || replace(gen_random_uuid()::text, '-', '');
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  IF p_tier NOT IN ('SPOTLIGHT', 'SUPER_BOOST') THEN RAISE EXCEPTION 'Invalid boost tier'; END IF;
  IF (p_tier = 'SPOTLIGHT' AND p_credits <> 100) OR (p_tier = 'SUPER_BOOST' AND p_credits <> 250) THEN
    RAISE EXCEPTION 'Invalid credit cost for boost tier';
  END IF;

  SELECT username INTO v_username FROM public.profiles WHERE user_id = v_uid;
  IF v_username IS NULL THEN v_username := 'momi.creator'; END IF;

  SELECT balance INTO v_balance FROM public.wallets WHERE user_id = v_uid FOR UPDATE;
  IF v_balance IS NULL OR v_balance < p_credits THEN
    RAISE EXCEPTION 'Insufficient wallet credits';
  END IF;

  UPDATE public.posts
  SET is_boosted = true,
      boost_tier = p_tier,
      recommended_label = CASE WHEN p_tier = 'SUPER_BOOST' THEN 'Super Boosted' ELSE 'Boosted Spotlight' END,
      boosted_by_uid = v_uid,
      boosted_at = now()
  WHERE id = p_post_id AND user_id = v_uid;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Post not found or not owned by caller';
  END IF;

  UPDATE public.wallets
  SET balance = balance - p_credits,
      total_spent = total_spent + p_credits,
      last_tx_id = v_tx_id
  WHERE user_id = v_uid;

  v_label := CASE WHEN p_tier = 'SUPER_BOOST' THEN 'Super Boosted' ELSE 'Boosted Spotlight' END;

  INSERT INTO public.credit_transactions(
    tx_id, type, sender_uid, sender_username, recipient_uid, recipient_username,
    credits_amount, price_usd, payment_method, post_id, recipient_claimed, status
  ) VALUES (
    v_tx_id, 'POST_BOOST', v_uid, v_username, v_uid, v_username,
    p_credits, 0, 'WALLET_CREDITS', p_post_id, true, 'COMPLETED'
  );

  RETURN jsonb_build_object('txId', v_tx_id, 'label', v_label, 'newBalance', v_balance - p_credits);
END;
$$;

-- 4.2 Atomic Live Gift Transfer
CREATE OR REPLACE FUNCTION public.send_live_gift(
  p_recipient_uid text,
  p_stream_id text,
  p_gift_key text,
  p_gift_name text,
  p_credits bigint
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid text := auth.uid()::text;
  v_sender_balance bigint;
  v_sender_name text;
  v_recipient_name text;
  v_tx_id text := 'tx_gift_' || replace(gen_random_uuid()::text, '-', '');
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  IF v_uid = p_recipient_uid THEN RAISE EXCEPTION 'Cannot gift yourself'; END IF;
  IF p_credits <= 0 OR p_credits > 5000 THEN RAISE EXCEPTION 'Invalid gift credit amount'; END IF;

  SELECT username INTO v_sender_name FROM public.profiles WHERE user_id = v_uid;
  SELECT username INTO v_recipient_name FROM public.profiles WHERE user_id = p_recipient_uid;

  SELECT balance INTO v_sender_balance FROM public.wallets WHERE user_id = v_uid FOR UPDATE;
  IF v_sender_balance IS NULL OR v_sender_balance < p_credits THEN
    RAISE EXCEPTION 'Insufficient wallet credits';
  END IF;

  UPDATE public.wallets
  SET balance = balance - p_credits,
      total_spent = total_spent + p_credits,
      last_tx_id = v_tx_id
  WHERE user_id = v_uid;

  INSERT INTO public.wallets(user_id, balance, total_earned, last_tx_id)
  VALUES (p_recipient_uid, p_credits, p_credits, v_tx_id)
  ON CONFLICT (user_id) DO UPDATE
  SET balance = public.wallets.balance + EXCLUDED.balance,
      total_earned = public.wallets.total_earned + EXCLUDED.total_earned,
      last_tx_id = EXCLUDED.last_tx_id;

  IF p_stream_id IS NOT NULL AND length(p_stream_id) > 0 THEN
    UPDATE public.live_streams
    SET gifts_credits = gifts_credits + p_credits
    WHERE id = p_stream_id;
  END IF;

  INSERT INTO public.credit_transactions(
    tx_id, type, sender_uid, sender_username, recipient_uid, recipient_username,
    credits_amount, price_usd, payment_method, gift_key, gift_name, stream_id, recipient_claimed, status
  ) VALUES (
    v_tx_id, 'GIFT', v_uid, coalesce(v_sender_name, 'momi.user'),
    p_recipient_uid, coalesce(v_recipient_name, 'momi.creator'),
    p_credits, 0, 'WALLET_CREDITS', p_gift_key, p_gift_name, p_stream_id, true, 'COMPLETED'
  );

  RETURN jsonb_build_object('txId', v_tx_id, 'newBalance', v_sender_balance - p_credits);
END;
$$;

-- 4.3 Owner Grant to Consenting Creator
CREATE OR REPLACE FUNCTION public.owner_grant_credits(
  p_target_uid text,
  p_credits bigint,
  p_perk_key text DEFAULT NULL,
  p_grant_verified boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_admin_uid text := auth.uid()::text;
  v_consent boolean;
  v_target_username text;
  v_admin_username text;
  v_tx_id text := 'tx_owner_' || replace(gen_random_uuid()::text, '-', '');
BEGIN
  IF NOT public.is_momi_admin() THEN
    RAISE EXCEPTION 'Owner admin authorization required';
  END IF;
  IF p_credits < 0 OR p_credits > 5000 THEN
    RAISE EXCEPTION 'Credits grant out of allowed range (0..5000)';
  END IF;

  SELECT partner_consent, username INTO v_consent, v_target_username
  FROM public.profiles WHERE user_id = p_target_uid;

  IF v_target_username IS NULL THEN
    RAISE EXCEPTION 'Target creator profile not found';
  END IF;
  IF coalesce(v_consent, false) = false THEN
    RAISE EXCEPTION 'Target creator has not opted in to Partner Boost & Perks Consent';
  END IF;

  SELECT username INTO v_admin_username FROM public.profiles WHERE user_id = v_admin_uid;

  INSERT INTO public.wallets(user_id, balance, total_earned, unlocked_perks, last_tx_id)
  VALUES (
    p_target_uid,
    p_credits,
    p_credits,
    CASE WHEN p_perk_key IS NOT NULL AND length(p_perk_key) > 0 THEN ARRAY[p_perk_key] ELSE ARRAY[]::text[] END,
    v_tx_id
  )
  ON CONFLICT (user_id) DO UPDATE
  SET balance = public.wallets.balance + p_credits,
      total_earned = public.wallets.total_earned + p_credits,
      unlocked_perks = CASE
        WHEN p_perk_key IS NOT NULL AND length(p_perk_key) > 0 AND NOT (p_perk_key = ANY(public.wallets.unlocked_perks))
          THEN array_append(public.wallets.unlocked_perks, p_perk_key)
        ELSE public.wallets.unlocked_perks
      END,
      last_tx_id = v_tx_id;

  IF p_grant_verified THEN
    UPDATE public.profiles SET is_verified = true WHERE user_id = p_target_uid;
  END IF;

  INSERT INTO public.credit_transactions(
    tx_id, type, sender_uid, sender_username, recipient_uid, recipient_username,
    credits_amount, price_usd, payment_method, perk_key, recipient_claimed, status
  ) VALUES (
    v_tx_id, 'OWNER_GRANT', coalesce(v_admin_uid, 'admin'), coalesce(v_admin_username, 'momi'),
    p_target_uid, v_target_username, p_credits, 0, 'OWNER_ADMIN', p_perk_key, true, 'COMPLETED'
  );

  RETURN jsonb_build_object('txId', v_tx_id, 'targetUid', p_target_uid, 'creditsGranted', p_credits);
END;
$$;

-- 4.4 Unlock Account Perk with Wallet Credits
CREATE OR REPLACE FUNCTION public.unlock_momi_perk(
  p_perk_key text,
  p_credits bigint
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid text := auth.uid()::text;
  v_balance bigint;
  v_username text;
  v_perks text[];
  v_new_tier text;
  v_new_ver text;
  v_tx_id text := 'tx_perk_' || replace(gen_random_uuid()::text, '-', '');
BEGIN
  IF v_uid IS NULL THEN RAISE EXCEPTION 'Authentication required'; END IF;
  IF (p_perk_key = 'SUPPORTER_FRAME' AND p_credits <> 100)
     OR (p_perk_key = 'CREATOR_BOOST' AND p_credits <> 250)
     OR (p_perk_key = 'PRIORITY_VERIFICATION' AND p_credits <> 500)
     OR (p_perk_key = 'VIP_PATRON_PASS' AND p_credits <> 1000) THEN
    RAISE EXCEPTION 'Invalid perk key or credit amount';
  END IF;

  SELECT username INTO v_username FROM public.profiles WHERE user_id = v_uid;
  SELECT balance, unlocked_perks INTO v_balance, v_perks FROM public.wallets WHERE user_id = v_uid FOR UPDATE;

  IF v_balance IS NULL OR v_balance < p_credits THEN
    RAISE EXCEPTION 'Insufficient wallet credits';
  END IF;
  IF p_perk_key = ANY(coalesce(v_perks, ARRAY[]::text[])) THEN
    RAISE EXCEPTION 'Perk already unlocked';
  END IF;

  v_perks := array_append(coalesce(v_perks, ARRAY[]::text[]), p_perk_key);
  v_new_tier := CASE
    WHEN 'VIP_PATRON_PASS' = ANY(v_perks) THEN 'VIP'
    WHEN 'CREATOR_BOOST' = ANY(v_perks) OR 'PRIORITY_VERIFICATION' = ANY(v_perks) THEN 'CREATOR_PRO'
    WHEN 'SUPPORTER_FRAME' = ANY(v_perks) THEN 'SUPPORTER'
    ELSE 'STANDARD'
  END;
  v_new_ver := CASE
    WHEN 'PRIORITY_VERIFICATION' = ANY(v_perks) THEN 'PENDING_PERK'
    WHEN 'CREATOR_BOOST' = ANY(v_perks) OR 'VIP_PATRON_PASS' = ANY(v_perks) THEN 'CREATOR_PRO'
    ELSE 'NONE'
  END;

  UPDATE public.wallets
  SET balance = balance - p_credits,
      total_spent = total_spent + p_credits,
      unlocked_perks = v_perks,
      account_tier = v_new_tier,
      verification_tier = CASE WHEN verification_tier = 'VERIFIED' THEN 'VERIFIED' ELSE v_new_ver END,
      last_tx_id = v_tx_id
  WHERE user_id = v_uid;

  INSERT INTO public.credit_transactions(
    tx_id, type, sender_uid, sender_username, recipient_uid, recipient_username,
    credits_amount, price_usd, payment_method, perk_key, recipient_claimed, status
  ) VALUES (
    v_tx_id, 'PERK_UNLOCK', v_uid, coalesce(v_username, 'momi.creator'),
    v_uid, coalesce(v_username, 'momi.creator'), p_credits, 0, 'WALLET_CREDITS', p_perk_key, true, 'COMPLETED'
  );

  RETURN jsonb_build_object('txId', v_tx_id, 'perkKey', p_perk_key, 'newBalance', v_balance - p_credits);
END;
$$;

-- ----------------------------------------------------------------------------
-- 5. Supabase Storage Buckets & RLS Policies (Replaces Base64 Media Chunks)
-- ----------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES
  ('momi-media', 'momi-media', true),
  ('momi-avatars', 'momi-avatars', true),
  ('momi-videos', 'momi-videos', true)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS momi_storage_public_read ON storage.objects;
CREATE POLICY momi_storage_public_read ON storage.objects
  FOR SELECT TO anon, authenticated
  USING (bucket_id IN ('momi-media', 'momi-avatars', 'momi-videos'));

DROP POLICY IF EXISTS momi_storage_auth_upload ON storage.objects;
CREATE POLICY momi_storage_auth_upload ON storage.objects
  FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id IN ('momi-media', 'momi-avatars', 'momi-videos')
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS momi_storage_owner_delete ON storage.objects;
CREATE POLICY momi_storage_owner_delete ON storage.objects
  FOR DELETE TO authenticated
  USING (
    bucket_id IN ('momi-media', 'momi-avatars', 'momi-videos')
    AND ((storage.foldername(name))[1] = auth.uid()::text OR public.is_momi_admin())
  );
