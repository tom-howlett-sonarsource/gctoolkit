# General principles
* Use red-green development, code coverage on new code must be >80%
* When generating code prioritise security, reliability and maintainability. This code will need to be maintained for many years and will be used by 10000s of developers. 
* Do not add unecessary code, remove any code that is not used, avoid duplication, extract common functionality
* Follow existing design patterns unless instructed not to
* Do not look for existing solutions in git history

# GCToolKit test data and local verification
Some tests depend on the external `gctoolkit-testdata` archive downloaded by the `gclogs` module. Do not rely only on synthetic unit tests when changing code that handles GC log discovery, archive formats, tail reading, metadata, parser input, or Vertx log sources.

Before marking those changes complete:
1. Run the smallest relevant unit or module tests first.
2. Run at least one real-fixture smoke test through the `gclogs` module so the downloaded test data is present.

Recommended smoke test:

```bash
mvn -pl gclogs,vertx -am \
  -Dtest=GarbageCollectionLogMetaDataTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -DfailIfNoTests=false \
  test
```

To avoid repeated slow downloads:
* Reuse an existing `gctoolkit-testdata-*.zip` when present.
* Do not redownload test data unless the configured `gctoolkit-testdata-version` changed or the archive is missing or corrupt.
* If the archive is already available in `/private/tmp`, copy it into the repo root before running `gclogs`.
* If the test-data download is blocked by sandbox or network limits, request escalation and report clearly if verification could not be completed.

If a Surefire JVM fails with `Java heap space`, inspect `target/surefire-reports/*.dump` and `*.dumpstream` before changing heap sizes or CI config.
