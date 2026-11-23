-- ============================================================================
-- Momi Starter Creators, Posts, Stories & Market Seed for Supabase
-- ============================================================================

-- 1. Profiles
INSERT INTO public.profiles (user_id, username, display_name, category, bio, avatar_key, is_verified, role)
VALUES
  ('creator_aria', 'aria.studio', 'Aria Chen', 'Architecture & Design', 'Minimalist architectural frames, brutalism & shadow play.', 'avatar_aria', true, 'USER'),
  ('creator_elena', 'elena.maris', 'Elena Maris', 'Travel & Coast', 'Amalfi sunlight, salty horizons, and Mediterranean wanderlust.', 'avatar_elena', true, 'USER'),
  ('creator_kenji', 'kenji.visuals', 'Kenji Sato', 'Street & Neon', 'Tokyo rain, cyberpunk reflections & late-night ramen spots.', 'avatar_kenji', true, 'USER'),
  ('creator_matcha', 'matcha.ritual', 'Matcha Ritual', 'Cafe & Food', 'Kyoto ceramic mornings, whisking matcha, and slow bakes.', 'avatar_matcha', false, 'USER'),
  ('creator_nord', 'nordic.light', 'Nora Lindqvist', 'Minimal & Nature', 'Fjord mornings, pine mist, and Scandinavian stillness.', 'avatar_nord', false, 'USER')
ON CONFLICT (user_id) DO UPDATE
SET username = EXCLUDED.username,
    display_name = EXCLUDED.display_name,
    category = EXCLUDED.category,
    bio = EXCLUDED.bio,
    avatar_key = EXCLUDED.avatar_key,
    is_verified = EXCLUDED.is_verified;

-- 2. Posts
INSERT INTO public.posts (id, user_id, author_username, author_display_name, author_avatar_key, author_verified, location, image_key, media_type, filter_tag, aspect_ratio, hashtags, caption, likes_count, category, is_boosted, boost_tier, recommended_label)
VALUES
  ('seed_post_1', 'creator_elena', 'elena.maris', 'Elena Maris', 'avatar_elena', true, 'Positano, Italy', 'img_post_coastal', 'IMAGE', 'WARM', '4:5', ARRAY['amalfi', 'goldenhour', 'travel', 'momi'], 'Golden hour spilling across the harbor stairs before sunset boats head back. #amalfi #goldenhour #momi', 342, 'Travel', true, 'OWNER_RECOMMENDED', 'Recommended by Momi'),
  ('seed_post_2', 'creator_matcha', 'matcha.ritual', 'Matcha Ritual', 'avatar_matcha', false, 'Kyoto, Japan', 'img_post_coffee', 'IMAGE', 'VINTAGE', '1:1', ARRAY['matcha', 'coffee', 'kyoto', 'aesthetic'], 'First brew of the rainy season in Higashiyama. #matcha #coffee #kyoto #aesthetic', 215, 'Cafe & Food', false, '', ''),
  ('seed_post_3', 'creator_aria', 'aria.studio', 'Aria Chen', 'avatar_aria', true, 'Basel, Switzerland', 'img_post_architecture', 'IMAGE', 'BLUE', '1:1', ARRAY['architecture', 'minimal', 'modern', 'momi'], 'Curved atrium concrete catching 10 AM geometric daylight. #architecture #minimal #modern', 189, 'Architecture', true, 'SPOTLIGHT', 'Boosted Spotlight'),
  ('seed_post_4', 'creator_kenji', 'kenji.visuals', 'Kenji Sato', 'avatar_kenji', true, 'Shinjuku, Tokyo', 'img_post_neon_street', 'IMAGE', 'NOIR', '16:9', ARRAY['tokyo', 'neon', 'street', 'nightwalk'], 'Midnight street reflections on wet asphalt. #tokyo #neon #street #nightwalk', 408, 'Street', true, 'SUPER_BOOST', 'Super Boosted')
ON CONFLICT (id) DO UPDATE
SET caption = EXCLUDED.caption,
    likes_count = EXCLUDED.likes_count,
    is_boosted = EXCLUDED.is_boosted,
    boost_tier = EXCLUDED.boost_tier,
    recommended_label = EXCLUDED.recommended_label;

-- 3. Stories
INSERT INTO public.stories (id, user_id, username, avatar_key, image_key, media_type, caption)
VALUES
  ('seed_story_1', 'creator_elena', 'elena.maris', 'avatar_elena', 'img_post_coastal', 'IMAGE', 'Morning swim in Positano'),
  ('seed_story_2', 'creator_matcha', 'matcha.ritual', 'avatar_matcha', 'img_post_coffee', 'IMAGE', 'Ceramic cup kiln opening today'),
  ('seed_story_3', 'creator_kenji', 'kenji.visuals', 'avatar_kenji', 'img_post_neon_street', 'IMAGE', 'Rain starting in Shibuya')
ON CONFLICT (id) DO NOTHING;

-- 4. Marketplace Products
INSERT INTO public.market_products (id, seller_uid, seller_username, store_name, title, description, category, price_usd, original_price_usd, discount_percent, free_shipping, image_key, stock_count, is_featured)
VALUES
  ('seed_prod_1', 'creator_aria', 'aria.studio', 'Aria Studio', 'Handmade Leather Camera Strap', 'Full-grain Italian leather quick-release strap built for mirrorless cameras.', 'Collectibles', 45.00, 55.00, 18, true, 'img_post_architecture', 15, true),
  ('seed_prod_2', 'creator_matcha', 'matcha.ritual', 'Matcha Pottery', 'Glazed Ceramic Chawan Bowl', 'Hand-thrown stoneware bowl for matcha preparation, made in Kyoto.', 'Collectibles', 38.00, 42.00, 9, true, 'img_post_coffee', 8, true),
  ('seed_prod_3', 'creator_kenji', 'kenji.visuals', 'Kenji Prints', 'Tokyo Rain Metallic Fine Art Print', 'Archival 12x18 metallic photo print captured in Kabukicho.', 'Collectibles', 60.00, 75.00, 20, true, 'img_post_neon_street', 25, false)
ON CONFLICT (id) DO NOTHING;
