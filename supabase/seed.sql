-- ============================================================================
-- Momi Clean Seed: App Config & Starter Showcase Accounts Only
-- ============================================================================
-- Preserves clean user directory (no unsolicited fake user profiles or posts).
-- Starter showcase personas live under Admin authority in public.showcase_accounts.

INSERT INTO public.app_config (id, latest_version_name)
VALUES ('monetization', '2.3')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.showcase_accounts (username, display_name, avatar_key, bio, category, location, is_verified, is_authorized, updated_by_email)
VALUES
  ('elena.maris', 'Elena Maris', 'avatar_elena', 'Amalfi sunlight, salty horizons, and Mediterranean wanderlust.', 'Travel', 'Positano, Amalfi Coast', true, false, 'syali6511@gmail.com'),
  ('matcha.ritual', 'Matcha Ritual', 'avatar_matcha', 'Kyoto ceramic mornings, stone-ground matcha, and slow bakes.', 'Coffee', 'Lisbon, Chiado', true, false, 'syali6511@gmail.com'),
  ('atelier.nord', 'Atelier Nord', 'avatar_nord', 'Minimalist architectural frames, raw concrete & warm oak.', 'Architecture', 'Copenhagen, Denmark', true, false, 'syali6511@gmail.com'),
  ('kenji.lens', 'Kenji Takahashi', 'avatar_kenji', 'Tokyo rain, neon reflections & late-night street photography.', 'Night', 'Shinjuku, Tokyo', true, false, 'syali6511@gmail.com'),
  ('sora.frames', 'Sora Nakamura', 'avatar_sora', 'Analog film grain and morning espresso rituals.', 'Food', 'Kyoto, Gion', true, false, 'syali6511@gmail.com'),
  ('layla.travels', 'Layla Haddad', 'avatar_layla', 'Aegean sunsets and coastal visual diaries.', 'Travel', 'Santorini, Greece', true, false, 'syali6511@gmail.com')
ON CONFLICT (username) DO NOTHING;
