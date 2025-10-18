# GitHub Repository Setup

This document contains the commands to initialize and push this project to GitHub.

## Files Prepared

The following files have been created/updated for GitHub:

1. `.gitignore` - Excludes build artifacts, sensitive files, and blog posts
2. `README.md` - Comprehensive project documentation
3. `infra/values-override.yaml.example` - Template for Helm values (actual file excluded)

## Before You Push

### 1. Clean Up Local Files

Remove any TLS certificates and keys from the project root:

```bash
cd /Users/matthewstobo/Documents/mqtt5SRDemo

# Remove TLS files from root (if present)
rm -f *.crt *.key *.pem

# Verify they're gone
ls -la *.crt *.key *.pem 2>/dev/null || echo "Cleanup complete - no sensitive files found"
```

### 2. Review Excluded Files

The `.gitignore` file excludes:
- Blog posts (BLOG_POST*.md)
- Sensitive configuration (`values-override.yaml`)
- TLS certificates and keys (`*.key`, `*.crt`, `*.pem`)
- Build artifacts (`target/`, `*.class`)
- Docker images (`*.tar.gz`)
- AWS and Kubernetes config

### 3. Verify Sensitive Data Removed

Double-check that no sensitive data is in files that will be tracked:

```bash
cd /Users/matthewstobo/Documents/mqtt5SRDemo

# Check for AWS account IDs
grep -r "804666467877" src/ infra/scripts/ *.md 2>/dev/null || echo "No AWS account ID found in source"

# Check for IP addresses
grep -r "18.216.87.178\|3.149.189.200" src/ infra/scripts/ *.md 2>/dev/null || echo "No IP addresses found in source"

# List what will actually be committed (preview)
git init  # If not already initialized
git add .
git status
```

**Important**: The `README.md` and `GITHUB_SETUP.md` contain example AWS account IDs and IPs as placeholders or documentation - this is OK since they're clearly marked as examples.

## Initialize Git Repository

```bash
cd /Users/matthewstobo/Documents/mqtt5SRDemo

# Initialize git
git init

# Add all files (respecting .gitignore)
git add .

# Check what will be committed
git status

# Create initial commit
git commit -m "Initial commit: MQTT5 + Solace Schema Registry demo

- MQTT5 publisher and subscriber with schema validation
- End-to-end data integrity with SERDES
- AWS EKS infrastructure templates
- CloudFormation and Helm deployment configs
- Comprehensive README and documentation"
```

## Connect to GitHub

```bash
# Add remote (replace with your repository URL if different)
git remote add origin https://github.com/mstobo/mqtt5SRDemo.git

# Verify remote
git remote -v

# Push to GitHub
git push -u origin main
```

If `main` branch doesn't exist, Git may default to `master`:
```bash
# Rename branch to main if needed
git branch -M main
git push -u origin main
```

## If Repository Already Exists on GitHub

If you've already created the repository on GitHub:

```bash
# Pull any existing content (like README from GitHub)
git pull origin main --allow-unrelated-histories

# Then push your code
git push -u origin main
```

## After Pushing

### Configure Repository Settings (on GitHub.com)

1. **Description**: Add a description
   ```
   MQTT5 + Solace Schema Registry integration demo with end-to-end schema validation, deployed on AWS EKS with CloudFormation and Helm
   ```

2. **Topics**: Add relevant topics
   ```
   mqtt5, schema-registry, solace, aws-eks, kubernetes, helm, json-schema, cloudformation, java
   ```

3. **Security**:
   - Enable Dependabot alerts
   - Enable secret scanning
   - Review any detected secrets

### Create Additional Documentation (Optional)

Consider adding these files later:
- `CONTRIBUTING.md` - Contribution guidelines
- `LICENSE` - Choose an appropriate license (MIT, Apache 2.0, etc.)
- `.github/workflows/` - CI/CD with GitHub Actions
- `.github/ISSUE_TEMPLATE/` - Issue templates

## Verify Repository

After pushing, verify on GitHub:

1. Check all expected files are present
2. Confirm `.gitignore` is working (blog posts, sensitive files should NOT appear)
3. Test clone in a different directory:
   ```bash
   cd /tmp
   git clone https://github.com/mstobo/mqtt5SRDemo.git
   cd mqtt5SRDemo
   ls -la
   ```

## Maintenance

### Updating Repository

```bash
# Make changes
git add .
git commit -m "Description of changes"
git push
```

### Syncing Fork (if others contribute)

```bash
git fetch origin
git merge origin/main
```

## Security Reminders

- **Never commit**:
  - AWS credentials
  - Passwords or API keys
  - TLS private keys
  - `values-override.yaml` with real values
  - Docker registry credentials

- **If you accidentally commit sensitive data**:
  1. Don't just delete and recommit (history persists)
  2. Use `git filter-branch` or BFG Repo-Cleaner
  3. Force push: `git push --force`
  4. Rotate ALL exposed credentials immediately

## Support

For issues with this repository:
- Open an issue on GitHub
- Check existing issues for solutions
- Refer to the comprehensive README.md

---

Repository: https://github.com/mstobo/mqtt5SRDemo

