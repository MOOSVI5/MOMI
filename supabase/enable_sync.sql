-- ============================================================================
-- Momi Direct Migration Sync Policies (PostgreSQL Valid Syntax)
-- ============================================================================
-- Run this once in Supabase SQL Editor if migrating rows without a Supabase JWT.

DROP POLICY IF EXISTS profiles_sync_write ON public.profiles;
CREATE POLICY profiles_sync_write ON public.profiles
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS posts_sync_write ON public.posts;
CREATE POLICY posts_sync_write ON public.posts
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS stories_sync_write ON public.stories;
CREATE POLICY stories_sync_write ON public.stories
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS merchants_sync_write ON public.merchants;
CREATE POLICY merchants_sync_write ON public.merchants
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS products_sync_write ON public.market_products;
CREATE POLICY products_sync_write ON public.market_products
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS app_config_sync_write ON public.app_config;
CREATE POLICY app_config_sync_write ON public.app_config
  FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
