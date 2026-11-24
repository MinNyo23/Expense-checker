-- Applied to the Expense-checker Supabase project.
-- The Android app uses Supabase Auth and direct RLS-scoped tables; it does not call
-- these legacy password/account RPCs. Keep the functions for historical compatibility,
-- but do not expose them through the public PostgREST API.
REVOKE EXECUTE ON FUNCTION public.change_account_password(text, text, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.login_couple_account(text, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.register_couple_account(text, text, text, text, text, boolean) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.current_user_couple_id() FROM PUBLIC;

-- Cover the foreign keys reported by the Supabase performance advisor.
CREATE INDEX IF NOT EXISTS idx_account_link_codes_couple_id
    ON public.account_link_codes(couple_id);
CREATE INDEX IF NOT EXISTS idx_device_sessions_couple_id
    ON public.device_sessions(couple_id);
CREATE INDEX IF NOT EXISTS idx_user_accounts_couple_id
    ON public.user_accounts(couple_id);
