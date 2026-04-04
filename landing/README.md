# SafePhone focus landing (static site)

This folder is deployed to **Cloudflare Pages** via [`.github/workflows/deploy-landing.yml`](../.github/workflows/deploy-landing.yml) (no GitHub Pages setup required).

## One-time setup

1. Note your **Account ID** (Workers & Pages sidebar, or **My Profile** → **API Tokens**).
2. Create an **API Token** with **Account** → **Cloudflare Pages** → **Edit** (or a template that includes Pages).
3. In the GitHub repo → **Settings** → **Secrets and variables** → **Actions**, add:
   - `CLOUDFLARE_API_TOKEN` — the token from step 2  
   - `CLOUDFLARE_ACCOUNT_ID` — from step 1  
4. The workflow runs `wrangler pages project create` before the first deploy so the project **`safephone-focus-landing`** exists. If you prefer to create it yourself: **Workers & Pages** → **Create** → **Pages** → **Create application** (direct upload / empty project) and use the **exact** same name as in [`.github/workflows/deploy-landing.yml`](../.github/workflows/deploy-landing.yml).
5. If that name is taken on your account, change `--project-name=...` in both workflow steps (`project create` and `deploy`) and use the matching `https://<name>.pages.dev/` URL below.

### “Project not found” (code 8000007)

The Pages project must exist before `pages deploy`. Re-run the workflow after pulling the latest workflow (it creates the project first), or create the project manually in the dashboard / once locally:

```bash
npx wrangler pages project create safephone-focus-landing --production-branch=main
```

## Canonical URL

After the first successful workflow run, the site is available at:

`https://safephone-focus-landing.pages.dev/`

(Replace the subdomain if you changed `project-name` in the workflow.)

Set `safephone.blockLandingUrl` in the root [`gradle.properties`](../gradle.properties) to that exact URL (a trailing slash is fine). Optional: `safephone.blockLandingUrl.debug` overrides the value for debug builds only. Rebuild the app so blocked browser exits open this page first.

## Manual deploy (optional)

With [Wrangler](https://developers.cloudflare.com/workers/wrangler/install-and-update/) installed and logged in (`wrangler login`):

```bash
npx wrangler pages deploy landing --project-name=safephone-focus-landing
```

## Custom domain (optional)

In Cloudflare Pages → your project → **Custom domains**, attach a domain you control. Then set `safephone.blockLandingUrl` to `https://your.domain/` (and rebuild the app).
