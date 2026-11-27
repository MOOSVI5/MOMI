-- ============================================================================
-- Momi Account & Data Cleanup Script
-- ============================================================================
-- Keeps ONLY:
--   1. Admin account: syali6511@gmail.com
--   2. Real user account: hassan.ali62313@gmail.com
--   3. Starter Showcase Accounts table (public.showcase_accounts) under Admin authority
-- Removes all other profile accounts, seed posts/stories/products, and orphaned data.
-- ============================================================================

DO $$
DECLARE
  admin_uid text;
  keep_uids text[];
BEGIN
  SELECT user_id INTO admin_uid
  FROM public.profiles
  WHERE lower(coalesce(email, '')) = 'syali6511@gmail.com'
  ORDER BY created_at ASC
  LIMIT 1;

  SELECT coalesce(array_agg(user_id), ARRAY[]::text[]) INTO keep_uids
  FROM public.profiles
  WHERE lower(coalesce(email, '')) IN ('syali6511@gmail.com', 'hassan.ali62313@gmail.com');

  -- If Admin exists, reassign any active custom showcase posts/stories to Admin's UID
  -- before deleting legacy showcase_* profile rows so Admin retains showcase content.
  IF admin_uid IS NOT NULL THEN
    UPDATE public.posts
    SET user_id = admin_uid
    WHERE id LIKE 'showcase_%'
      AND user_id <> admin_uid;

    UPDATE public.stories
    SET user_id = admin_uid
    WHERE id LIKE 'showcase_%'
      AND user_id <> admin_uid;

    UPDATE public.comments
    SET user_id = admin_uid
    WHERE id LIKE 'showcase_%'
      AND user_id <> admin_uid;
  END IF;

  -- Remove legacy template seed posts, stories, and marketplace items
  DELETE FROM public.posts WHERE id LIKE 'seed_post_%';
  DELETE FROM public.stories WHERE id LIKE 'seed_story_%';
  DELETE FROM public.market_products WHERE id LIKE 'seed_prod_%';

  -- Remove non-whitelisted threads, messages, reports, pages, groups, orders & transactions
  DELETE FROM public.chat_messages
  WHERE NOT (sender_uid = ANY(keep_uids));

  DELETE FROM public.threads
  WHERE NOT (user_a_id = ANY(keep_uids) AND user_b_id = ANY(keep_uids));

  DELETE FROM public.page_follows WHERE NOT (user_id = ANY(keep_uids));
  DELETE FROM public.pages WHERE NOT (owner_uid = ANY(keep_uids));

  DELETE FROM public.group_members WHERE NOT (user_id = ANY(keep_uids));
  DELETE FROM public.groups WHERE NOT (owner_uid = ANY(keep_uids));

  DELETE FROM public.market_orders
  WHERE NOT (buyer_uid = ANY(keep_uids) OR seller_uid = ANY(keep_uids));

  DELETE FROM public.credit_transactions
  WHERE NOT (sender_uid = ANY(keep_uids) OR recipient_uid = ANY(keep_uids));

  DELETE FROM public.content_reports
  WHERE NOT (reporter_uid = ANY(keep_uids) OR target_uid = ANY(keep_uids));

  -- Delete all remaining profiles except syali6511@gmail.com and hassan.ali62313@gmail.com
  -- Foreign keys with ON DELETE CASCADE clean up their posts, comments, stories, follows,
  -- user_interactions, notifications, live_streams, merchants, market_products & wallets.
  DELETE FROM public.profiles
  WHERE lower(coalesce(email, '')) NOT IN ('syali6511@gmail.com', 'hassan.ali62313@gmail.com');
END $$;
