# Publishing and verification

The project is intended for a separate public repository. If authentication is unavailable, the exact publishing commands from the repository root are:

```sh
gh auth login --hostname github.com --git-protocol https --web
git init -b main
git add .
git commit -m "Build IdentityPulse authentication reliability platform"
gh repo create identitypulse-reliability-platform --public --source=. --remote=origin --push
# If the repository already exists:
# git remote add origin https://github.com/YOUR_USER/identitypulse-reliability-platform.git
# git push -u origin main
gh repo view --json name,url,isPrivate
gh run list --branch main --limit 10
# Inspect each CI and CodeQL run ID, then:
gh run watch RUN_ID --exit-status
```

A repository existing and a commit being pushed do not imply checks passed. Record workflow URLs and head SHA in docs/results.md after observing completed checks. Failed jobs require correction; a manual rerun does not alter the source commit. GitHub artifacts retain test/chaos output for the configured retention period; copy reviewed measurements into version-controlled evidence when needed.
