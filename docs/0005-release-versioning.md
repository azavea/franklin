# 5. Release versioning

Date: 2021-05-07

## Status

Proposed

## Context

To this point Franklin has been publishing un-versioned container images. This process has continued over several STAC and STAC API specification versions, rounds of feedback from users, bugfixes for things I accidentally broke while "making things better," and versions of extension handling. After all of this, without a notion of a "release," to start versoining releases.

Traditionally, Azavea's projects have used _semantic versioning_. This has always been a bit confusing in the application (rather than library) setting. The question of "breaking" changes is more difficult -- Franklin isn't a library consumed by other in-language programs, but an application consumed by anything that speaks HTTP and JSON. If a client and Franklin are wrong in the same way, and then we fix it, is that a breaking change or a bug fix?

The purpose of this ADR is to decide on and justify the choice for a specific versioning scheme. Different schemes will be evaluated based on:

- how easy is it to tell what the next version should be?
- how do we know when to cut a release (vs. just publishing the commit SHA container)?
- how easy is it for a consumer to tell what's new?

This ADR will discuss semantic versioning, date versioning (like [MinIO]), and "browser versioning" (a continuously increasing integer).

## Decision

The change that we're proposing or have agreed to implement.

## Consequences

What becomes easier or more difficult to do and any risks introduced by the change that will need to be mitigated.

[MinIO]: https://hub.docker.com/r/minio/minio/tags?page=1&ordering=last_updated