# SafePhone focus landing (static site)

This folder is deployed to **Cloudflare Pages** via [`.github/workflows/deploy-landing.yml`](../.github/workflows/deploy-landing.yml) (no GitHub Pages setup required).

## One-time setup

1. You only need a Cloudflare account; the first GitHub Actions run creates the Pages project `safephone-focus-landing` when you deploy (no need to connect the repo inside Cloudflare unless you prefer their UI).
2. Note your **Account ID** (Workers & Pages → any subdomain sidebar, or **My Profile** → **API Tokens** page).
3. Create an **API Token** with **Account** → **Cloudflare Pages** → **Edit** (template “Edit Cloudflare Workers” also works if it includes Pages).
4. In the GitHub repo → **Settings** → **Secrets and variables** → **Actions**, add:
   - `CLOUDFLARE_API_TOKEN` — the token from step 3  
   - `CLOUDFLARE_ACCOUNT_ID` — from step 2  
5. If the project name `safephone-focus-landing` is taken or you want another name, change `--project-name=...` in the workflow and use the matching URL below.

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
