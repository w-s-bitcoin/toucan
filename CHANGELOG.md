# Changelog

## Unreleased

- Correct LifeHash color arithmetic to match the pinned Blockchain Commons C++
  reference. Existing icons may change after updating Toucan: 300 of 20,495
  sampled public fingerprints (1.46%) produced different pixels before this
  correction. Fingerprint values remain unchanged. Applications such as Sparrow
  should include this visual compatibility change in their release notes when
  adopting the corrected dependency.
