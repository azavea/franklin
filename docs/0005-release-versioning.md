# 5. Release versioning

Date: 2021-08-03

## Status

Accepted

## Context

To this point Franklin has been publishing un-versioned container images. This
process has continued over several STAC and STAC API specification versions,
rounds of feedback from users, bugfixes for things I accidentally broke while
"making things better," and versions of extension handling. After all of this,
without a notion of a "release," to start versoining releases.

Traditionally, Azavea's projects have used _semantic versioning_. This has
always been a bit confusing in the application (rather than library) setting.
The question of "breaking" changes is more difficult -- Franklin isn't a library
consumed by other in-language programs, but an application consumed by anything
that speaks HTTP and JSON. If a client and Franklin are wrong in the same way,
and then we fix it, is that a breaking change or a bug fix?

The purpose of this ADR is to decide on and justify the choice for a specific
versioning scheme.

This ADR will discuss semantic versioning, date versioning (like [MinIO]), and
"browser versioning" (a continuously increasing integer).

### Semantic versioning

"Semantic versioning" has been our default in a lot of settings. It's also the
[versioning scheme used by STAC]. This kind of consistency seems appealing. In
practice, our semantic versioning efforts in the past have been not quite
semantic, and they've been a bit confusing for applications.

For example, [Raster Foundry] has been in the 1.x series for over three years,
despite changes in required parameters and outright removal of some
functionality. The entire change in GroundWork's CHANGELOG (repository is
private) that indicated it was 1.0.0 time was "Promoted 0.51.3 to 1.0.0 --
released campaigns." It's easier to tell in `stac4s` what a breaking change is,
but for Franklin it's pretty challenging.

Is an application breaking change anything that adds a migration? Any upgrade to
`stac4s` that changes the shape of JSON? A change in required headers? A change
in behavior of some headers (for example, [allowing an asterisk in If-Match])?
We've never had answers to these questions, and I think "ad hoc move to 1.0,
then never move to 2.0" has been a pretty normal experience.


### Date versioning

Date versioning is a versioning scheme in which releases are tagged with their
date to some precision. In [MinIO]'s case that's down to the minute, and this
discussion will assume the same precision.

Date versioning makes no promises about visibility of breaking changes. The only
indication of the gap between version x and version y under date versioning is
how far apart they are in time. This means that as maintainers we _must_ publish
good release notes (a CHANGELOG would help with some of this) and _must_ publish
good migration guides for large version changes.

It's always easy in this case to determine the next version number -- it's
_right now_ in UTC as an ISO8601 string without a timezone. That makes the
mechanics of doing releases really straightforward and probably 100%
automatable. It's hard in this case for a user to tell if an upgrade is safe for
them in the absence of the supporting documentation mentioned above.

### "Browser versioning"

This term comes from discussion with other Azaveans of versioning schemes -- no
one had a specific word for it, but we thought of it as "what Chrome and Firefox
do," where there's a number that always goes up and it's not obvious what
anything means. For example, my current Firefox is on version 90.0.2 and Chrome
is 92.0.4515.107. [Chrome's versioning] has a decent amount of flexibility for
Chromium developers:

> MAJOR and MINOR may get updated with any significant Google Chrome release
> (Beta or Stable update). MAJOR must get updated for any backwards incompatible
> user data change (since this data survives updates).

Firefox doesn't appear to have much public documentation about their release
process, except that it's [rapid now] and is, if you squint, kind of like date
versioning if you want to do some math with six-week increments.

In this case, versions aren't quite semantic, but there's still some notion of
"big changes" vs. "little changes" vs. bug fixes.

## Decision

Franklin will choose date versioning. Semantic versioning can lead to false
confidence, and historically speaking it's hard for applications (though the
Chromium versioning advice offers a useful heuristic -- invalidation of old data
is _always major_). Browser versioning might trick people into thinking the
versions are semantic while still requiring the supporting documentation. Date
versioning is honest about the information it presents -- release x was Tuesday
at 3pm UTC, and release y was Thursday at 7pm UTC. Changelog maintenance and
release announcements will mitigate the information that SemVer purports to
offer, and bundling the changelog and a release announcement into the docs site
that's already built in CI will keep the overall maintenance burden manageable.

## Consequences

Several tasks come out of this decision. We'll need to:

- create a CHANGELOG
- add a `release-announcements/` directory that gets published somewhere
  reasonable as part of the docs site publication workflow
- christen the first release `2021-09-02T18:00:00` when it's ready 🎉

[MinIO]: https://hub.docker.com/r/minio/minio/tags?page=1&ordering=last_updated
[versioning scheme used by STAC]: https://github.com/radiantearth/stac-spec#current-version-and-branches
[Raster Foundry]: https://github.com/raster-foundry/raster-foundry/blob/develop/CHANGELOG.md
[allowing an asterisk in If-Match]: https://github.com/azavea/franklin/issues/781
[Chrome's versioning]: https://www.chromium.org/developers/version-numbers
[rapid now]: https://support.mozilla.org/en-US/questions/896705
