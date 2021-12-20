# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Deployment instructions with AWS Copilot CLI. Note that this PR downgraded the version of AWS Java SDK to `2.16.13`, which aligns with the version used in [GeoTrellis](https://github.com/locationtech/geotrellis/blob/v3.6.0/project/Dependencies.scala#L86). This had something related to issues with accessing S3 from the Fargate ECS instance.  [#1081](https://github.com/azavea/franklin/pull/1081)
