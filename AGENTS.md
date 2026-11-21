# DawnShell Working Rules

## Branches

- Do all new work, bug fixes, and documentation changes on the `dev` or `feat/<feature-name>` branch.
- Do not work on `main` directly or push directly to `main`.
- Commit and verify against `dev`.
- Merge into `main` only when creating a new release. Regular feature development, bug fixes,
  documentation changes, or CI fixes alone must not be merged into `main`.
- The release workflow targets pushes to `main` or version tags, so work that is not a release must not
  be applied to `main` directly.

## Starting Work

```sh
git switch dev
git pull --ff-only origin dev
```

If `dev` does not exist yet, create it once from the latest `main` of the repository.

```sh
git switch -c dev main
git push -u origin dev
```

## Changes and Verification

- Preserve existing user changes and work-in-progress changes.
- Commit in meaningful units, and use the `type: description` format for commit messages.
- Run the tests and documentation checks that match the code changes.
- When verification on a real device is required, do not report the untested state as a success.
- Do not push to a remote, create a release, or move a tag unless the user explicitly asks for it.

## Releases

- Even when creating a release, finish the build and verification on `dev` first.
- An official release is made by merging `dev` into `main` after verification is complete and then
  creating a version tag. Follow this procedure only for new release work.
- Do not overwrite existing GitHub Releases and their assets.

## Documentation

- Record user-facing behavior changes in both the relevant English and Korean documents.
- When changing repository structure, build, installation, compatibility, or security behavior, update the
  README and the relevant `docs/` documents.
