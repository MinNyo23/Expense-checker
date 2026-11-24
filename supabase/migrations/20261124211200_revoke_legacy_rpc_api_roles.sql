-- The initial PUBLIC revoke did not remove explicit PostgREST role grants.
-- Remove API execution for the unused legacy RPC functions from both client roles.
REVOKE EXECUTE ON FUNCTION public.change_account_password(text, text, text)
    FROM anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.login_couple_account(text, text)
    FROM anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.register_couple_account(text, text, text, text, text, boolean)
    FROM anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.rls_auto_enable()
    FROM anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.current_user_couple_id()
    FROM anon, authenticated;
