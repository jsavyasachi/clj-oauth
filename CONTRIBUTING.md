# Contributing to clj-oauth

Send bug reports, fixes, and focused feature contributions for `clj-oauth`.

## Before you start

- For work beyond a trivial fix, **open an issue first**. We can agree on the
  approach before you spend time.
- Check existing issues and pull requests to avoid duplicate work.

## Development

This is a Clojure library built with `deps.edn` and the
[Clojure CLI](https://clojure.org/guides/install_clojure); Leiningen is not
required. You need a JDK and the Clojure CLI. See the README for the full set
of aliases.

```bash
clojure -M:test    # run the test suite (compiled with *warn-on-reflection* on)
```

The bar for a mergeable change:

- **Tests first.** Add or update tests for the behavior you change. For a bug
  fix, add a regression test that fails before the fix and passes after it.
- **Green build.** The test suite passes and the build reports **zero**
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
