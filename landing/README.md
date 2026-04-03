# SafePhone focus landing (static site)

This folder is deployed to **GitHub Pages** via [`.github/workflows/pages.yml`](../.github/workflows/pages.yml).

## Canonical URL

After the first successful deploy, your public URL will look like:

`https://<github-username-or-org>.github.io/<repository-name>/`

Set `safephone.blockLandingUrl` in the root [`gradle.properties`](../gradle.properties) to that exact URL (a trailing slash is fine). Optional: `safephone.blockLandingUrl.debug` overrides the value for debug builds only. Rebuild the app so blocked browser exits open this page first.

In the GitHub repository, enable **Settings → Pages → Build and deployment → Source: GitHub Actions**.

## Custom domain (optional)

In the repository **Settings → Pages**, add a custom domain and create the DNS records GitHub shows. Then set `BLOCK_LANDING_URL` to `https://your.domain/`.
