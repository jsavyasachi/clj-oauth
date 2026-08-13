# Contributing to clj-oauth

Send bug reports, fixes, and focused feature contributions for `clj-oauth`.

## Before you start

- For work beyond a trivial fix, **open an issue first**. We can agree on the
  approach before you spend time.
- Check existing issues and pull requests to avoid duplicate work.

## Development

This is a Clojure library. You need a JDK and [Leiningen](https://leiningen.org/).
Projects that use `deps.edn` use the Clojure CLI. See the README.

```bash
lein test     # run the test suite
lein check    # AOT-compile; must be free of reflection warnings
```

The bar for a mergeable change:

- **Tests first.** Add or update tests for the behavior you change. For a bug
  fix, add a regression test that fails before the fix and passes after it.
- **Green build.** `lein test` passes and `lein check` reports **zero**
  reflection warnings.
- **One scope.** Keep each pull request to one logical change.

## Commits and pull requests

- Follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` …).
- Keep the subject in the imperative mood and under ~72 characters.
- Update `CHANGELOG.md` if users can see your change.
- Rebase on the latest `main` before opening the pull request.

## License

When you contribute, you agree to license your contributions under the same
license as this project. See `LICENSE` and the README.
