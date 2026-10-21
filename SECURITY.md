# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| 1.0.x (latest `main`) | Yes |
| older | No |

## Reporting a vulnerability

Please **do not** report security vulnerabilities in public issues, discussions or pull requests.

Report them privately through GitHub's private vulnerability reporting:

1. Go to the repository's **[Security tab](https://github.com/cryptotechdao/harkquant-terminal/security)**.
2. Click **Report a vulnerability**
   ([direct link](https://github.com/cryptotechdao/harkquant-terminal/security/advisories/new)).
3. Describe the issue, the affected version or commit, steps to reproduce and the impact you expect.

You can expect an acknowledgement within 7 days. We will keep you updated while we investigate, agree on a
disclosure date with you, and credit you in the advisory unless you prefer to stay anonymous.

## Scope

HarkQuant Terminal is a self-hosted, read-only tool. Areas of particular interest:

- The settings/secrets API and the `HQT_TOKEN` write guard (`hqterm/config.py`, `hqterm/app.py`)
- Leakage of API keys stored in `state/secrets.json` to the browser or logs
- Cross-site scripting through news headlines or other server text in the web UI or News Desk
- The Content Security Policy and service worker (`static/`)
- Token storage and network security configuration in the Android app (`android/`)

Out of scope: vulnerabilities in third-party data providers or their APIs, and issues that require an attacker to
already control the machine running the server.

## Hardening tips for self-hosters

- Keep the server on `127.0.0.1` unless you need LAN access, and set `HQT_TOKEN` whenever it listens on other
  interfaces.
- Put a public deployment behind HTTPS (a reverse proxy such as Caddy or nginx).
- Never commit `state/`, keystores or signing properties.
